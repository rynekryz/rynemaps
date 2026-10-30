package com.rynekryz.rynemaps.data

import org.json.JSONObject

data class GeoPoint(val lat: Double, val lon: Double)

data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

data class MapTap(val point: GeoPoint, val name: String?, val kind: String?, val markerId: String?)

data class Place(
    val id: String,
    val name: String,
    val subtitle: String,
    val address: String,
    val point: GeoPoint,
    val icon: String = "location_on",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("subtitle", subtitle)
        .put("address", address)
        .put("lat", point.lat)
        .put("lon", point.lon)
        .put("icon", icon)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            id = o.getString("id"),
            name = o.getString("name"),
            subtitle = o.optString("subtitle"),
            address = o.optString("address"),
            point = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
            icon = o.optString("icon", "location_on"),
        )
    }
}

val MY_LOCATION = Place("me", "Your location", "", "", GeoPoint(0.0, 0.0), "my_location")

enum class TravelMode(val label: String, val icon: String, val server: String) {
    Drive("Drive", "directions_car", "routed-car"),
    Walk("Walk", "directions_walk", "routed-foot"),
    Bike("Bike", "directions_bike", "routed-bike"),
}

enum class MapStyleOption(val label: String, val icon: String, val url: String) {
    Default("Default", "map", "https://tiles.openfreemap.org/styles/liberty"),
    Detailed("Detailed", "terrain", "https://tiles.openfreemap.org/styles/bright"),
    Minimal("Minimal", "layers", "https://tiles.openfreemap.org/styles/positron"),
    Night("Night", "dark_mode", "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"),
}

data class RouteStep(
    val text: String,
    val icon: String,
    val distance: Double,
    val duration: Double,
    val point: GeoPoint,
)

class RouteInfo(
    val points: List<GeoPoint>,
    val distance: Double,
    val duration: Double,
    val steps: List<RouteStep>,
) {
    val cumulative: DoubleArray by lazy {
        val c = DoubleArray(points.size)
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
        c
    }
}
