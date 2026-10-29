package com.rynekryz.rynemaps.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "RyMaps/1.0 (com.rynekryz.rynemaps)")
                    .build()
            )
        }
        .build()

    private suspend fun fetch(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    private fun photonLang(): String {
        val l = Locale.getDefault().language
        return if (l in setOf("en", "de", "fr", "it")) l else "en"
    }

    suspend fun search(query: String, near: GeoPoint?): List<Place> {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "10")
            .addQueryParameter("lang", photonLang())
        near?.let {
            url.addQueryParameter("lat", it.lat.toString())
            url.addQueryParameter("lon", it.lon.toString())
        }
        val body = fetch(Request.Builder().url(url.build()).build())
        val features = JSONObject(body).getJSONArray("features")
        return buildList {
            for (i in 0 until features.length()) parsePhoton(features.getJSONObject(i))?.let { add(it) }
        }
    }

    private fun parsePhoton(f: JSONObject): Place? {
        val props = f.getJSONObject("properties")
        val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
        val point = GeoPoint(coords.getDouble(1), coords.getDouble(0))
        val hasName = props.str("name").isNotBlank()
        val street = listOf(props.str("street"), props.str("housenumber"))
            .filter { it.isNotBlank() }.joinToString(" ")
        val title = if (hasName) props.str("name") else street
        if (title.isBlank()) return null
        val rest = buildList {
            if (hasName && street.isNotBlank()) add(street)
            for (k in listOf("city", "district", "state", "country")) {
                val v = props.str(k)
                if (v.isNotBlank() && v != title) add(v)
            }
        }.distinct()
        val id = props.str("osm_type") + props.optLong("osm_id") + "@" + point.lat + "," + point.lon
        return Place(
            id = id,
            name = title,
            subtitle = rest.take(3).joinToString(", "),
            address = rest.joinToString(", "),
            point = point,
            icon = iconFor(props.str("osm_key"), props.str("osm_value")),
        )
    }

    suspend fun reverse(p: GeoPoint): Place {
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("lat", p.lat.toString())
            .addQueryParameter("lon", p.lon.toString())
            .addQueryParameter("zoom", "18")
            .addQueryParameter("addressdetails", "1")
            .build()
        val o = JSONObject(fetch(Request.Builder().url(url).build()))
        val addr = o.optJSONObject("address")
        val road = listOfNotNull(addr?.str("road"), addr?.str("house_number"))
            .filter { it.isNotBlank() }.joinToString(" ")
        val name = o.str("name").ifBlank { road }.ifBlank { "Dropped pin" }
        val kind = o.str("type").replace('_', ' ').replaceFirstChar { it.uppercase() }
        return Place(
            id = "rev",
            name = name,
            subtitle = if (o.str("name").isNotBlank()) kind else "",
            address = o.str("display_name"),
            point = p,
            icon = iconFor(o.str("category"), o.str("type")),
        )
    }

    suspend fun nearby(cat: Category, south: Double, west: Double, north: Double, east: Double): List<Place> {
        val filter = "[\"${cat.key}\"~\"^(${cat.values})\$\"]"
        val q = "[out:json][timeout:20];nwr$filter($south,$west,$north,$east);out center 80;"
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(FormBody.Builder().add("data", q).build())
            .build()
        val elements = JSONObject(fetch(req)).getJSONArray("elements")
        return buildList {
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                val point = if (el.has("lat")) {
                    GeoPoint(el.getDouble("lat"), el.getDouble("lon"))
                } else {
                    val c = el.optJSONObject("center") ?: continue
                    GeoPoint(c.getDouble("lat"), c.getDouble("lon"))
                }
                val name = tags.str("name").ifBlank { tags.str("brand") }.ifBlank { cat.label.removeSuffix("s") }
                val street = listOf(tags.str("addr:street"), tags.str("addr:housenumber"))
                    .filter { it.isNotBlank() }.joinToString(" ")
                val address = listOf(street, tags.str("addr:city")).filter { it.isNotBlank() }.joinToString(", ")
                add(
                    Place(
                        id = "osm:" + el.getString("type") + el.getLong("id"),
                        name = name,
                        subtitle = cat.label.removeSuffix("s"),
                        address = address,
                        point = point,
                        icon = cat.icon,
                    )
                )
            }
        }
    }

    suspend fun route(from: GeoPoint, to: GeoPoint, mode: TravelMode): RouteInfo {
        val url = "https://routing.openstreetmap.de/${mode.server}/route/v1/driving/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}" +
            "?overview=full&geometries=geojson&steps=true"
        val o = JSONObject(fetch(Request.Builder().url(url).build()))
        if (o.str("code") != "Ok") throw IOException("No route")
        val r = o.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = List(coords.length()) {
            val c = coords.getJSONArray(it)
            GeoPoint(c.getDouble(1), c.getDouble(0))
        }
        if (points.size < 2) throw IOException("No route")
        val steps = buildList {
            val legs = r.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val arr = legs.getJSONObject(l).getJSONArray("steps")
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val m = s.getJSONObject("maneuver")
                    val loc = m.getJSONArray("location")
                    val type = m.str("type")
                    val modifier = m.str("modifier").ifBlank { null }
                    val exit = if (m.has("exit")) m.optInt("exit") else null
                    add(
                        RouteStep(
                            text = Maneuvers.text(type, modifier, s.str("name"), exit),
                            icon = Maneuvers.icon(type, modifier),
                            distance = s.getDouble("distance"),
                            duration = s.getDouble("duration"),
                            point = GeoPoint(loc.getDouble(1), loc.getDouble(0)),
                        )
                    )
                }
            }
        }
        return RouteInfo(points, r.getDouble("distance"), r.getDouble("duration"), steps)
    }

    fun iconFor(key: String, value: String): String = when (value) {
        "restaurant", "fast_food", "food_court" -> "restaurant"
        "cafe" -> "local_cafe"
        "bar", "pub" -> "local_bar"
        "fuel" -> "local_gas_station"
        "supermarket", "convenience", "grocery", "greengrocer" -> "local_grocery_store"
        "hotel", "hostel", "motel", "guest_house" -> "hotel"
        "pharmacy" -> "local_pharmacy"
        "hospital", "clinic", "doctors" -> "local_hospital"
        "bank" -> "account_balance"
        "atm" -> "local_atm"
        "school", "university", "college" -> "school"
        "park", "garden" -> "park"
        "bus_stop", "bus_station", "bus" -> "directions_bus"
        "station", "railway", "halt", "train_station" -> "train"
        "aerodrome", "airport" -> "flight"
        "parking" -> "local_parking"
        "cinema" -> "movie"
        "museum" -> "museum"
        "library" -> "local_library"
        "police" -> "local_police"
        else -> when (key) {
            "place", "boundary" -> "location_city"
            "shop" -> "shopping_bag"
            "highway" -> "signpost"
            else -> "location_on"
        }
    }
}

object Maneuvers {
    fun text(type: String, modifier: String?, name: String, exit: Int?): String {
        val onto = if (name.isNotBlank()) " onto $name" else ""
        val along = if (name.isNotBlank()) " on $name" else ""
        val dir = modifier.orEmpty()
        return when (type) {
            "depart" -> if (name.isNotBlank()) "Head$along" else "Start your route"
            "arrive" -> when (dir) {
                "left" -> "Arrive at your destination, on the left"
                "right" -> "Arrive at your destination, on the right"
                else -> "Arrive at your destination"
            }
            "roundabout", "rotary", "roundabout turn" ->
                if (exit != null) "At the roundabout, take exit $exit$onto" else "Enter the roundabout$onto"
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
            "merge" -> "Merge${if (dir.isNotBlank()) " $dir" else ""}$onto"
            "on ramp", "off ramp" -> "Take the ramp${if (dir.isNotBlank()) " on the $dir" else ""}$onto"
            "fork" -> "Keep ${dir.ifBlank { "straight" }} at the fork$onto"
            "end of road" -> "Turn ${dir.ifBlank { "ahead" }} at the end of the road$onto"
            "new name" -> "Continue$onto"
            else -> when (dir) {
                "uturn" -> "Make a U-turn$onto"
                "straight", "" -> "Continue straight$along"
                else -> "Turn $dir$onto"
            }
        }
    }

    fun icon(type: String, modifier: String?): String = when (type) {
        "arrive" -> "sports_score"
        "depart" -> "navigation"
        "roundabout", "rotary", "roundabout turn" -> "roundabout_right"
        "merge" -> "merge"
        "fork" -> if (modifier?.contains("left") == true) "fork_left" else "fork_right"
        else -> when (modifier) {
            "left" -> "turn_left"
            "right" -> "turn_right"
            "slight left" -> "turn_slight_left"
            "slight right" -> "turn_slight_right"
            "sharp left" -> "turn_sharp_left"
            "sharp right" -> "turn_sharp_right"
            "uturn" -> "u_turn_left"
            else -> "straight"
        }
    }
}
