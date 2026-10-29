@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.rynemaps.data.MY_LOCATION
import com.rynekryz.rynemaps.data.MapStyleOption
import com.rynekryz.rynemaps.ui.map.RyMapView
import com.rynekryz.rynemaps.ui.theme.Symbol
import kotlin.math.abs

@Composable
fun RyMapsApp(vm: MapViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    var bearing by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        vm.onPermission(granted.values.any { it })
    }
    fun hasPermission() = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun locate() {
        if (hasPermission()) vm.follow() else launcher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        val ok = hasPermission()
        vm.onPermission(ok)
        if (!ok) launcher.launch(permissions)
    }
    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val nav = state.navigation
    val directions = state.directions
    val navigating = nav != null
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = state.canGoBack) { vm.back() }

    val styleUrl = (state.mapStyle ?: if (dark) MapStyleOption.Night else MapStyleOption.Default).url
    val pin = directions?.destination?.takeIf { it.id != MY_LOCATION.id } ?: state.selected
    val route = nav?.route?.points ?: directions?.route?.points
    val exploring = directions == null && state.tab == Tab.Explore
    val showNav = nav == null && directions == null && state.selected == null && state.activeCategory == null

    Box(Modifier.fillMaxSize()) {
        RyMapView(
            styleUrl = styleUrl,
            route = route,
            markers = if (directions == null) state.markers else emptyList(),
            selected = pin,
            location = state.location,
            hasPermission = state.hasPermission,
            commands = vm.camera,
            attributionBottomPx = sheetHeight,
            onTap = vm::onMapTap,
            onLongPress = vm::onMapLongPress,
            onBearing = { bearing = it },
            onFollowing = vm::setFollowing,
            onViewport = vm::setViewport,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.tab == Tab.Saved && directions == null) {
            SavedScreen(state.saved, onOpen = vm::openSaved, onRemove = vm::removeSaved)
        }

        if (exploring) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) {
                SearchPill(
                    onClick = { vm.openSearch(SearchTarget.Main) },
                    onAvatar = { showAbout = true },
                )
                Spacer(Modifier.size(8.dp))
                CategoryChips(state.activeCategory, vm::selectCategory)
            }
            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 132.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(
                    onClick = { vm.showLayers(true) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { Symbol("layers") }
                if (abs(bearing) > 1f) {
                    SmallFloatingActionButton(
                        onClick = vm::resetNorth,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.error,
                    ) { Symbol("navigation", modifier = Modifier.rotate(-bearing)) }
                }
            }
        }

        if (directions != null && nav == null) {
            DirectionsTopCard(
                d = directions,
                onBack = vm::closeDirections,
                onSwap = vm::swapEnds,
                onEditOrigin = { vm.openSearch(SearchTarget.Origin) },
                onEditDestination = { vm.openSearch(SearchTarget.Destination) },
                onMode = vm::setMode,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            )
        }
        if (nav != null) {
            NavigationBanner(nav, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                    if (nav != null) {
                        if (!state.following) {
                            ExtendedFloatingActionButton(
                                onClick = vm::follow,
                                icon = { Symbol("navigation") },
                                text = { Text("Re-center") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else if (state.tab == Tab.Explore) {
                        SmallFloatingActionButton(
                            onClick = { locate() },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (state.following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ) { Symbol(if (state.following) "my_location" else "location_searching") }
                        if (directions == null) {
                            FloatingActionButton(
                                onClick = { vm.startDirections(state.selected) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) { Symbol("directions", size = 28.dp) }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().animateContentSize().onSizeChanged { sheetHeight = it.height },
            ) {
                when {
                    nav != null -> NavigationBottomBar(nav, onExit = vm::exitNavigation)
                    directions != null -> DirectionsSheet(
                        d = directions,
                        canStart = directions.origin.id == MY_LOCATION.id && state.hasPermission,
                        onStart = vm::startNavigation,
                        onToggleSteps = vm::toggleSteps,
                        onRetry = vm::loadRoute,
                    )
                    state.tab == Tab.Explore && state.selected != null -> PlaceSheet(
                        place = state.selected!!,
                        saved = state.saved.any { it.id == state.selected!!.id },
                        loading = state.loadingPlace,
                        onClose = vm::clearSelection,
                        onDirections = { vm.startDirections(state.selected) },
                        onSave = vm::toggleSaved,
                        onShare = { sharePlace(context, state.selected!!) },
                    )
                    state.tab == Tab.Explore && state.activeCategory != null -> ResultsSheet(
                        category = state.activeCategory!!,
                        results = state.markers,
                        loading = state.categoryLoading,
                        onClose = vm::clearCategory,
                        onSelect = { vm.selectPlace(it) },
                    )
                }
                if (showNav) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = state.tab == Tab.Explore,
                            onClick = { vm.setTab(Tab.Explore) },
                            icon = { Symbol("explore") },
                            label = { Text("Explore") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { vm.startDirections(null) },
                            icon = { Symbol("directions_car") },
                            label = { Text("Go") },
                        )
                        NavigationBarItem(
                            selected = state.tab == Tab.Saved,
                            onClick = { vm.setTab(Tab.Saved) },
                            icon = { Symbol("bookmarks") },
                            label = { Text("Saved") },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.searchTarget != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SearchOverlay(
                state = state,
                target = state.searchTarget ?: SearchTarget.Main,
                onQuery = vm::setQuery,
                onBack = vm::closeSearch,
                onPick = vm::pickResult,
                onMyLocation = vm::pickMyLocation,
            )
        }
    }

    if (state.layersOpen) {
        LayersSheet(
            current = state.mapStyle ?: if (dark) MapStyleOption.Night else MapStyleOption.Default,
            onSelect = vm::setMapStyle,
            onDismiss = { vm.showLayers(false) },
        )
    }
    if (showAbout) AboutDialog { showAbout = false }
}
