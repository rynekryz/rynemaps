package com.rynekryz.rynemaps.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rynekryz.rynemaps.data.GeoPoint
import com.rynekryz.rynemaps.data.MapTap
import com.rynekryz.rynemaps.data.Place
import com.rynekryz.rynemaps.ui.CameraCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
private const val ROUTE_SRC = "ry-route-src"
private const val SELECTED_SRC = "ry-selected-src"
private const val ROUTE_CASING = "ry-route-casing"
private const val ROUTE_LINE = "ry-route-line"
private const val SELECTED_LAYER = "ry-selected-layer"
private const val PIN = "ry-pin"
private val ROUTE_BLUE = Color.parseColor("#1A73E8")
private val PIN_RED = Color.parseColor("#EA4335")

private class MapHolder(val view: MapView) {
    var map: MapLibreMap? = null
    var style: Style? = null
    var destroyed = false
    var trackingListener: OnCameraTrackingChangedListener? = null
}

private fun pinBitmap(density: Float): Bitmap {
    val w = (30 * density).toInt()
    val h = (42 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = PIN_RED
    val r = w / 2f
    c.drawCircle(r, r, r, paint)
    val path = Path().apply {
        moveTo(w * 0.10f, r * 1.25f)
        lineTo(r, h.toFloat())
        lineTo(w * 0.90f, r * 1.25f)
        close()
    }
    c.drawPath(path, paint)
    paint.color = Color.WHITE
    c.drawCircle(r, r, r * 0.38f, paint)
    return bmp
}

private fun lineJson(points: List<GeoPoint>?): String {
    if (points.isNullOrEmpty()) return EMPTY
    val coords = JSONArray()
    points.forEach { coords.put(JSONArray().put(it.lon).put(it.lat)) }
    return JSONObject()
        .put("type", "Feature")
        .put("properties", JSONObject())
        .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords))
        .toString()
}

private fun pointsJson(places: List<Place>): String {
    if (places.isEmpty()) return EMPTY
    val features = JSONArray()
    places.forEach { p ->
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("id", p.id))
                .put(
                    "geometry",
                    JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.point.lon).put(p.point.lat))
                )
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

@Composable
fun RyMapView(
    styleUrl: String,
    route: List<GeoPoint>?,
    selected: Place?,
    location: Location?,
    hasPermission: Boolean,
    following: Boolean,
    commands: Flow<CameraCommand>,
    attributionBottomPx: Int,
    onTap: (MapTap) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    onBearing: (Float) -> Unit,
    onFollowing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val holder = remember { MapHolder(MapView(context)) }
    var mapReady by remember { mutableStateOf(false) }
    var styleVersion by remember { mutableIntStateOf(0) }
    val currentStyleUrl by rememberUpdatedState(styleUrl)
    val tapCb by rememberUpdatedState(onTap)
    val longCb by rememberUpdatedState(onLongPress)
    val bearingCb by rememberUpdatedState(onBearing)
    val followCb by rememberUpdatedState(onFollowing)
    val permissionNow by rememberUpdatedState(hasPermission)
    val followingNow by rememberUpdatedState(following)
    val pinImage = remember { pinBitmap(density) }

    fun setupStyle(style: Style) {
        holder.style = style
        style.addImage(PIN, pinImage)
        style.addSource(GeoJsonSource(ROUTE_SRC, EMPTY))
        style.addSource(GeoJsonSource(SELECTED_SRC, EMPTY))
        val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
        val casing = LineLayer(ROUTE_CASING, ROUTE_SRC).withProperties(
            lineColor(Color.WHITE), lineWidth(9f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        )
        val line = LineLayer(ROUTE_LINE, ROUTE_SRC).withProperties(
            lineColor(ROUTE_BLUE), lineWidth(6f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (firstSymbol != null) {
            style.addLayerBelow(casing, firstSymbol)
            style.addLayerBelow(line, firstSymbol)
        } else {
            style.addLayer(casing)
            style.addLayer(line)
        }
        style.addLayer(
            SymbolLayer(SELECTED_LAYER, SELECTED_SRC).withProperties(
                iconImage(PIN), iconSize(1f), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconAllowOverlap(true), iconIgnorePlacement(true),
            )
        )
        styleVersion++
    }

    fun loadStyle(map: MapLibreMap, url: String) {
        map.setStyle(Style.Builder().fromUri(url)) { setupStyle(it) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, holder) {
        val view = holder.view
        val observer = LifecycleEventObserver { _, event ->
            if (holder.destroyed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_CREATE -> view.onCreate(null)
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    holder.destroyed = true
                    view.onDestroy()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        view.getMapAsync { map ->
            holder.map = map
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
            map.setMinZoomPreference(2.0)
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(30.0, 10.0)).zoom(2.5).build()
                )
            )
            map.addOnMapClickListener { latLng ->
                val px = map.projection.toScreenLocation(latLng)
                var tap = MapTap(GeoPoint(latLng.latitude, latLng.longitude), null, null, null)
                for (f in map.queryRenderedFeatures(px)) {
                    val json = runCatching { JSONObject(f.toJson()) }.getOrNull() ?: continue
                    val geom = json.optJSONObject("geometry") ?: continue
                    if (geom.optString("type") != "Point") continue
                    val props = json.optJSONObject("properties") ?: continue
                    val name = props.optString("name")
                    if (name.isBlank()) continue
                    val c = geom.getJSONArray("coordinates")
                    tap = MapTap(GeoPoint(c.getDouble(1), c.getDouble(0)), name, props.optString("class"), null)
                    break
                }
                tapCb(tap)
                true
            }
            map.addOnMapLongClickListener { latLng ->
                longCb(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            map.addOnCameraMoveListener { bearingCb(map.cameraPosition.bearing.toFloat()) }
            loadStyle(map, currentStyleUrl)
            mapReady = true
        }
        onDispose {
            lifecycle.removeObserver(observer)
            if (!holder.destroyed) {
                holder.destroyed = true
                view.onPause()
                view.onStop()
                view.onDestroy()
            }
        }
    }

    var lastStyle by remember { mutableStateOf(styleUrl) }
    LaunchedEffect(styleUrl, mapReady) {
        val map = holder.map
        if (mapReady && map != null && styleUrl != lastStyle) {
            lastStyle = styleUrl
            loadStyle(map, styleUrl)
        }
    }

    LaunchedEffect(route, styleVersion) {
        val json = withContext(Dispatchers.Default) { lineJson(route) }
        holder.style?.getSourceAs<GeoJsonSource>(ROUTE_SRC)?.setGeoJson(json)
    }
    LaunchedEffect(selected, styleVersion) {
        val json = withContext(Dispatchers.Default) { pointsJson(listOfNotNull(selected)) }
        holder.style?.getSourceAs<GeoJsonSource>(SELECTED_SRC)?.setGeoJson(json)
    }

    LaunchedEffect(attributionBottomPx, mapReady) {
        holder.map?.uiSettings?.setAttributionMargins((12 * density).toInt(), 0, 0, attributionBottomPx + (4 * density).toInt())
    }

    LaunchedEffect(styleVersion, hasPermission) {
        val map = holder.map ?: return@LaunchedEffect
        val style = holder.style ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        val lc = map.locationComponent
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(false)
                .locationComponentOptions(LocationComponentOptions.builder(context).pulseEnabled(true).build())
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
        lc.renderMode = RenderMode.COMPASS
        holder.trackingListener?.let { lc.removeOnCameraTrackingChangedListener(it) }
        val listener = object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                followCb(false)
            }

            override fun onCameraTrackingChanged(currentMode: Int) {
                followCb(currentMode != CameraMode.NONE)
            }
        }
        holder.trackingListener = listener
        lc.addOnCameraTrackingChangedListener(listener)
        if (followingNow) {
            lc.renderMode = RenderMode.COMPASS
            lc.cameraMode = CameraMode.TRACKING
        }
    }

    LaunchedEffect(location, styleVersion, hasPermission) {
        val map = holder.map ?: return@LaunchedEffect
        if (!hasPermission || location == null) return@LaunchedEffect
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated) lc.forceLocationUpdate(location)
    }

    LaunchedEffect(mapReady) {
        if (!mapReady) return@LaunchedEffect
        commands.collect { cmd ->
            val map = holder.map ?: return@collect
            val lc = map.locationComponent
            when (cmd) {
                is CameraCommand.FlyTo -> {
                    if (lc.isLocationComponentActivated) lc.cameraMode = CameraMode.NONE
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(cmd.point.lat, cmd.point.lon), cmd.zoom),
                        800,
                    )
                }
                is CameraCommand.Fit -> {
                    if (cmd.points.size < 2) return@collect
                    if (lc.isLocationComponentActivated) lc.cameraMode = CameraMode.NONE
                    val b = LatLngBounds.Builder()
                    cmd.points.forEach { b.include(LatLng(it.lat, it.lon)) }
                    val pad = density
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            b.build(), (48 * pad).toInt(), (230 * pad).toInt(), (48 * pad).toInt(), (260 * pad).toInt()
                        ),
                        900,
                    )
                }
                is CameraCommand.Follow -> {
                    if (!permissionNow || !lc.isLocationComponentActivated) return@collect
                    if (cmd.on) {
                        if (cmd.navigation) {
                            lc.renderMode = RenderMode.GPS
                            lc.setCameraMode(CameraMode.TRACKING_GPS, 800L, 17.5, null, 55.0, null)
                        } else {
                            lc.renderMode = RenderMode.COMPASS
                            lc.setCameraMode(CameraMode.TRACKING, 800L, 15.5, null, 0.0, null)
                        }
                    } else {
                        lc.cameraMode = CameraMode.NONE
                        lc.renderMode = RenderMode.COMPASS
                    }
                }
                CameraCommand.ResetNorth -> {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(map.cameraPosition).bearing(0.0).tilt(0.0).build()
                        ),
                        500,
                    )
                }
            }
        }
    }

    AndroidView(factory = { holder.view }, modifier = modifier)
}
