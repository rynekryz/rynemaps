package com.rynekryz.rynemaps.ui

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.rynemaps.data.Api
import com.rynekryz.rynemaps.data.Geo
import com.rynekryz.rynemaps.data.GeoPoint
import com.rynekryz.rynemaps.data.LocationRepository
import com.rynekryz.rynemaps.data.MY_LOCATION
import com.rynekryz.rynemaps.data.MapStyleOption
import com.rynekryz.rynemaps.data.MapTap
import com.rynekryz.rynemaps.data.Place
import com.rynekryz.rynemaps.data.RouteInfo
import com.rynekryz.rynemaps.data.SavedStore
import com.rynekryz.rynemaps.data.Settings
import com.rynekryz.rynemaps.data.SettingsStore
import com.rynekryz.rynemaps.data.ThemeMode
import com.rynekryz.rynemaps.data.TravelMode
import com.rynekryz.rynemaps.data.UnitsMode
import com.rynekryz.rynemaps.data.VoiceGuide
import com.rynekryz.rynemaps.util.imperialCountries
import com.rynekryz.rynemaps.util.toGeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

enum class Tab { Explore, Saved }

enum class SearchTarget { Main, Origin, Destination }

sealed interface CameraCommand {
    data class FlyTo(val point: GeoPoint, val zoom: Double = 16.0) : CameraCommand
    data class Fit(val points: List<GeoPoint>) : CameraCommand
    data class Follow(val on: Boolean, val navigation: Boolean = false) : CameraCommand
    data object ResetNorth : CameraCommand
}

data class DirectionsState(
    val origin: Place = MY_LOCATION,
    val destination: Place? = null,
    val mode: TravelMode = TravelMode.Drive,
    val route: RouteInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val showSteps: Boolean = false,
)

data class NavState(
    val route: RouteInfo,
    val nextStep: Int,
    val distanceToNext: Double,
    val remainingDistance: Double,
    val remainingDuration: Double,
    val arrived: Boolean = false,
    val rerouting: Boolean = false,
)

data class UiState(
    val tab: Tab = Tab.Explore,
    val mapStyle: MapStyleOption? = null,
    val layersOpen: Boolean = false,
    val location: Location? = null,
    val hasPermission: Boolean = false,
    val following: Boolean = false,
    val selected: Place? = null,
    val loadingPlace: Boolean = false,
    val searchTarget: SearchTarget? = null,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
    val recents: List<Place> = emptyList(),
    val saved: List<Place> = emptyList(),
    val directions: DirectionsState? = null,
    val navigation: NavState? = null,
) {
    val canGoBack: Boolean
        get() = searchTarget != null || navigation != null || directions != null ||
            selected != null || tab != Tab.Explore
}

@OptIn(FlowPreview::class)
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SavedStore(app)
    private val settingsStore = SettingsStore(app)
    private val locations = LocationRepository(app)
    private val voiceGuide = VoiceGuide(app)

    private val _state = MutableStateFlow(UiState(saved = store.saved(), recents = store.recents()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _avatar = MutableStateFlow<Bitmap?>(null)
    val avatar: StateFlow<Bitmap?> = _avatar.asStateFlow()

    private val _camera = MutableSharedFlow<CameraCommand>(replay = 1, extraBufferCapacity = 16)
    val camera: SharedFlow<CameraCommand> = _camera.asSharedFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private var locationJob: Job? = null
    private var routeJob: Job? = null
    private var reverseJob: Job? = null
    private var lastGps = 0L
    private var offRouteCount = 0
    private var announcedStep = -1
    private var announcedNear = false
    private var announcedArrive = false

    init {
        viewModelScope.launch {
            _avatar.value = withContext(Dispatchers.IO) { settingsStore.readAvatar() }
        }
        viewModelScope.launch {
            queryFlow.debounce(300).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    _state.update { it.copy(results = emptyList(), searching = false, searchFailed = false) }
                    return@collectLatest
                }
                _state.update { it.copy(searching = true, searchFailed = false) }
                val res = runCatching { Api.search(q.trim(), _state.value.location?.toGeo()) }
                _state.update {
                    it.copy(
                        results = res.getOrDefault(emptyList()),
                        searching = false,
                        searchFailed = res.isFailure,
                    )
                }
            }
        }
        voiceGuide.enabled = _settings.value.voice
    }

    override fun onCleared() {
        voiceGuide.close()
        super.onCleared()
    }

    fun setVoice(on: Boolean) {
        voiceGuide.enabled = on
        if (!on) voiceGuide.stop()
        updateSettings { it.copy(voice = on) }
    }

    fun setName(name: String) {
        updateSettings { it.copy(name = name.trim().take(24)) }
    }

    fun setKeepScreenOn(on: Boolean) = updateSettings { it.copy(keepScreenOn = on) }

    fun testVoice() {
        voiceGuide.speak("Voice guidance is on. In 100 meters, turn left onto Example Street.")
    }

    fun setUnits(units: UnitsMode) = updateSettings { it.copy(units = units) }

    fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }

    private fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        settingsStore.save(next)
    }

    fun setAvatar(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching { settingsStore.setAvatar(uri) }.getOrNull()
            if (bmp != null) {
                _avatar.value = bmp
                updateSettings { it.copy(avatarVersion = it.avatarVersion + 1) }
            } else {
                _toasts.tryEmit("Couldn't use this image")
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.removeAvatar()
            _avatar.value = null
            updateSettings { it.copy(avatarVersion = it.avatarVersion + 1) }
        }
    }

    fun clearRecents() {
        store.clearRecents()
        _state.update { it.copy(recents = emptyList()) }
    }

    fun clearSaved() {
        store.clearSaved()
        _state.update { it.copy(saved = emptyList()) }
    }

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted && locationJob == null) {
            locationJob = viewModelScope.launch { locations.updates().collect { onLocation(it) } }
        }
    }

    private fun onLocation(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        if (loc.provider == LocationManager.GPS_PROVIDER) lastGps = now
        else if (now - lastGps < 15_000) return
        val first = _state.value.location == null
        _state.update { it.copy(location = loc) }
        if (first && _state.value.selected == null && _state.value.directions == null) {
            _camera.tryEmit(CameraCommand.FlyTo(loc.toGeo(), 14.5))
        }
        _state.value.navigation?.let { advance(it, loc) }
    }

    fun setFollowing(value: Boolean) = _state.update { it.copy(following = value) }

    fun follow() {
        val s = _state.value
        if (!s.hasPermission) _toasts.tryEmit("Waiting for your location")
        _state.update { it.copy(following = true) }
        _camera.tryEmit(CameraCommand.Follow(true, s.navigation != null))
    }

    fun resetNorth() {
        _camera.tryEmit(CameraCommand.ResetNorth)
    }

    fun setTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun showLayers(open: Boolean) = _state.update { it.copy(layersOpen = open) }

    fun setMapStyle(option: MapStyleOption) = _state.update { it.copy(mapStyle = option, layersOpen = false) }

    fun openSearch(target: SearchTarget, initial: String = "") {
        queryFlow.value = initial
        _state.update { it.copy(searchTarget = target, query = initial, results = emptyList(), searchFailed = false) }
    }

    fun setQuery(q: String) {
        queryFlow.value = q
        _state.update { it.copy(query = q) }
    }

    fun closeSearch() {
        _state.update {
            val d = it.directions
            it.copy(
                searchTarget = null,
                query = "",
                results = emptyList(),
                directions = if (d != null && d.destination == null) null else d,
            )
        }
    }

    fun pickResult(place: Place) {
        val s = _state.value
        when (s.searchTarget) {
            SearchTarget.Origin -> {
                _state.update { it.copy(directions = it.directions?.copy(origin = place), searchTarget = null, query = "") }
                loadRoute()
            }
            SearchTarget.Destination -> {
                remember(place)
                _state.update {
                    it.copy(
                        directions = (it.directions ?: DirectionsState()).copy(destination = place),
                        searchTarget = null,
                        query = "",
                    )
                }
                loadRoute()
            }
            else -> {
                remember(place)
                _state.update { it.copy(searchTarget = null, query = "", tab = Tab.Explore) }
                selectPlace(place)
            }
        }
    }

    fun pickMyLocation() {
        val target = _state.value.searchTarget
        if (target != SearchTarget.Origin && target != SearchTarget.Destination) return
        _state.update {
            val d = it.directions ?: DirectionsState()
            it.copy(
                directions = if (target == SearchTarget.Origin) d.copy(origin = MY_LOCATION) else d.copy(destination = MY_LOCATION),
                searchTarget = null,
                query = "",
            )
        }
        loadRoute()
    }

    private fun remember(place: Place) {
        store.addRecent(place)
        _state.update { it.copy(recents = store.recents()) }
    }

    fun selectPlace(place: Place, fly: Boolean = true) {
        reverseJob?.cancel()
        _state.update { it.copy(selected = place, loadingPlace = false) }
        if (fly) _camera.tryEmit(CameraCommand.FlyTo(place.point, 16.0))
    }

    fun clearSelection() {
        reverseJob?.cancel()
        _state.update { it.copy(selected = null, loadingPlace = false) }
    }

    private fun resolveAddress(place: Place, replaceName: Boolean) {
        reverseJob?.cancel()
        _state.update { it.copy(loadingPlace = true) }
        reverseJob = viewModelScope.launch {
            val r = runCatching { Api.reverse(place.point) }.getOrNull()
            _state.update { s ->
                val cur = s.selected
                if (cur == null || cur.id != place.id) s.copy(loadingPlace = false)
                else s.copy(
                    loadingPlace = false,
                    selected = cur.copy(
                        name = if (replaceName && r != null) r.name else cur.name,
                        subtitle = if (replaceName && r != null) r.subtitle else cur.subtitle,
                        icon = if (replaceName && r != null) r.icon else cur.icon,
                        address = r?.address ?: cur.address,
                    ),
                )
            }
        }
    }

    fun onMapTap(tap: MapTap) {
        if (tap.markerId != null) return
        val s = _state.value
        when {
            tap.name != null -> {
                if (s.directions != null) return
                val kind = tap.kind.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }
                val p = Place(
                    id = "tap:${tap.point.lat},${tap.point.lon}",
                    name = tap.name,
                    subtitle = kind,
                    address = "",
                    point = tap.point,
                    icon = Api.iconFor("", tap.kind.orEmpty()),
                )
                selectPlace(p, fly = false)
                resolveAddress(p, replaceName = false)
            }
            else -> if (s.selected != null && s.directions == null) clearSelection()
        }
    }

    fun onMapLongPress(point: GeoPoint) {
        if (_state.value.directions != null) return
        val p = Place("pin:${point.lat},${point.lon}", "Dropped pin", "", "", point, "location_on")
        selectPlace(p, fly = false)
        resolveAddress(p, replaceName = true)
    }

    fun toggleSaved() {
        val p = _state.value.selected ?: return
        store.toggle(p)
        _state.update { it.copy(saved = store.saved()) }
    }

    fun removeSaved(place: Place) {
        store.toggle(place)
        _state.update { it.copy(saved = store.saved()) }
    }

    fun openSaved(place: Place) {
        _state.update { it.copy(tab = Tab.Explore) }
        selectPlace(place)
    }

    fun startDirections(destination: Place?) {
        _state.update { it.copy(directions = DirectionsState(destination = destination), tab = Tab.Explore) }
        if (destination == null) openSearch(SearchTarget.Destination) else loadRoute()
    }

    fun closeDirections() {
        routeJob?.cancel()
        _state.update { it.copy(directions = null, navigation = null) }
    }

    fun setMode(mode: TravelMode) {
        _state.update { it.copy(directions = it.directions?.copy(mode = mode)) }
        loadRoute()
    }

    fun swapEnds() {
        val d = _state.value.directions ?: return
        val dest = d.destination ?: return
        _state.update { it.copy(directions = d.copy(origin = dest, destination = d.origin)) }
        loadRoute()
    }

    fun toggleSteps() = _state.update { it.copy(directions = it.directions?.copy(showSteps = !it.directions.showSteps)) }

    fun loadRoute() {
        val s = _state.value
        val d = s.directions ?: return
        val dest = d.destination ?: return
        val from = if (d.origin.id == MY_LOCATION.id) s.location?.toGeo() else d.origin.point
        val to = if (dest.id == MY_LOCATION.id) s.location?.toGeo() else dest.point
        if (from == null || to == null) {
            _state.update { it.copy(directions = d.copy(route = null, loading = false, error = "Waiting for your location. Enable location or choose a starting point.")) }
            return
        }
        routeJob?.cancel()
        _state.update { it.copy(directions = d.copy(loading = true, error = null, route = null)) }
        routeJob = viewModelScope.launch {
            val r = runCatching { Api.route(from, to, d.mode) }
            r.onSuccess { route ->
                _state.update { it.copy(directions = it.directions?.copy(route = route, loading = false, error = null)) }
                _camera.tryEmit(CameraCommand.Fit(route.points))
            }.onFailure {
                _state.update { it.copy(directions = it.directions?.copy(loading = false, error = "No route found between these places.")) }
            }
        }
    }

    fun startNavigation() {
        val s = _state.value
        val route = s.directions?.route ?: return
        if (s.location == null) {
            _toasts.tryEmit("Waiting for your location")
            return
        }
        val next = 1.coerceAtMost(route.steps.lastIndex).coerceAtLeast(0)
        offRouteCount = 0
        announcedStep = -1
        announcedNear = false
        announcedArrive = false
        voiceGuide.speak("Starting route")
        _state.update {
            it.copy(
                navigation = NavState(
                    route = route,
                    nextStep = next,
                    distanceToNext = route.steps.getOrNull(next)?.let { st -> Geo.distance(s.location.toGeo(), st.point) } ?: 0.0,
                    remainingDistance = route.distance,
                    remainingDuration = route.duration,
                ),
                following = true,
            )
        }
        _camera.tryEmit(CameraCommand.Follow(on = true, navigation = true))
    }

    fun exitNavigation() {
        voiceGuide.stop()
        _state.update { it.copy(navigation = null) }
        _camera.tryEmit(CameraCommand.Follow(on = false))
        viewModelScope.launch {
            _camera.tryEmit(CameraCommand.ResetNorth)
            delay(600)
            _state.value.directions?.let { d ->
                _camera.tryEmit(CameraCommand.FlyTo(d.destination?.point ?: return@launch, 15.5))
            }
        }
    }

    private fun advance(nav: NavState, loc: Location) {
        val p = loc.toGeo()
        val r = nav.route
        if (r.steps.isEmpty()) return
        val proj = Geo.project(r, p)
        val total = r.cumulative.last().coerceAtLeast(1.0)
        val frac = ((total - proj.along) / total).coerceIn(0.0, 1.0)
        val radius = maxOf(25.0, loc.speed * 2.5)
        var next = nav.nextStep.coerceIn(0, r.steps.lastIndex)
        while (next < r.steps.lastIndex && Geo.distance(p, r.steps[next].point) < radius) next++
        val distNext = Geo.distance(p, r.steps[next].point)
        val arrived = Geo.distance(p, r.points.last()) < 30.0
        _state.update {
            it.copy(
                navigation = nav.copy(
                    nextStep = next,
                    distanceToNext = distNext,
                    remainingDistance = r.distance * frac,
                    remainingDuration = r.duration * frac,
                    arrived = arrived,
                )
            )
        }
        announce(r, next, distNext, arrived)
        if (arrived || nav.rerouting) return
        if (proj.offset > 60.0) offRouteCount++ else offRouteCount = 0
        if (offRouteCount >= 3) reroute(loc)
    }

    private fun announce(r: RouteInfo, step: Int, dist: Double, arrived: Boolean) {
        if (!voiceGuide.enabled) return
        if (arrived) {
            if (!announcedArrive) {
                announcedArrive = true
                voiceGuide.speak("You have arrived at your destination")
            }
            return
        }
        val text = r.steps[step].text
        when (step) {
            announcedStep -> if (!announcedNear && dist < 40.0) {
                announcedNear = true
                voiceGuide.speak(text)
            }
            else -> {
                announcedStep = step
                announcedNear = dist <= 60.0
                if (announcedNear) voiceGuide.speak(text)
                else if (dist <= 400.0) voiceGuide.speak("In ${spoken(dist)}, $text")
            }
        }
    }

    private fun spoken(meters: Double): String {
        val imperial = when (_settings.value.units) {
            UnitsMode.Miles -> true
            UnitsMode.Km -> false
            UnitsMode.Auto -> Locale.getDefault().country in imperialCountries
        }
        return if (imperial) {
            val feet = meters * 3.28084
            if (feet > 1600) "${(feet / 5280).roundToInt()} miles" else "${(feet / 100).roundToInt() * 100} feet"
        } else {
            if (meters > 1000) "${(meters / 1000).roundToInt()} kilometers" else "${(meters / 10).roundToInt() * 10} meters"
        }
    }

    private fun reroute(loc: Location) {
        val d = _state.value.directions ?: return
        val dest = d.destination ?: return
        offRouteCount = 0
        announcedStep = -1
        voiceGuide.speak("Rerouting")
        _state.update { it.copy(navigation = it.navigation?.copy(rerouting = true)) }
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            runCatching { Api.route(loc.toGeo(), dest.point, d.mode) }
                .onSuccess { route ->
                    announcedNear = false
                    _state.update {
                        it.copy(
                            directions = it.directions?.copy(route = route),
                            navigation = it.navigation?.copy(
                                route = route,
                                nextStep = 1.coerceAtMost(route.steps.lastIndex).coerceAtLeast(0),
                                remainingDistance = route.distance,
                                remainingDuration = route.duration,
                                rerouting = false,
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(navigation = it.navigation?.copy(rerouting = false)) }
                    _toasts.tryEmit("Rerouting failed. Check your connection.")
                }
        }
    }

    fun back() {
        val s = _state.value
        when {
            s.searchTarget != null -> closeSearch()
            s.navigation != null -> exitNavigation()
            s.directions != null -> closeDirections()
            s.selected != null -> clearSelection()
            s.tab != Tab.Explore -> setTab(Tab.Explore)
        }
    }

    fun openUri(uri: Uri) {
        if (uri.scheme != "geo") return
        val ssp = uri.schemeSpecificPart.orEmpty()
        val coords = ssp.substringBefore('?').substringBefore(';').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull()
        val lon = coords.getOrNull(1)?.toDoubleOrNull()
        val q = ssp.substringAfter('?', "").split('&').firstOrNull { it.startsWith("q=") }
            ?.removePrefix("q=")?.let { Uri.decode(it) }
        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
            val label = q?.substringAfter('(', "")?.substringBefore(')').orEmpty()
            val p = Place("geo:$lat,$lon", label.ifBlank { "Dropped pin" }, "", "", GeoPoint(lat, lon))
            selectPlace(p)
            resolveAddress(p, replaceName = label.isBlank())
        } else if (!q.isNullOrBlank()) {
            openSearch(SearchTarget.Main, q)
        }
    }
}
