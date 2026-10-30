@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun RyMapsApp(vm: MapViewModel) {
    val haptics = rememberHaptics()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    var bearing by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLocationPrompt by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setAvatar(uri)
    }

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        vm.onPermission(granted.values.any { it })
    }
    fun hasPermission() = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun locate() {
        if (!hasPermission()) {
            launcher.launch(permissions)
            return
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val anyEnabled = listOf(
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false),
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false),
        ).any { it }
        if (!anyEnabled) {
            showLocationPrompt = true
            return
        }
        vm.follow()
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
    DisposableEffect(settings.keepScreenOn, navigating) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = state.canGoBack) { vm.back() }
    BackHandler(enabled = showSettings) { showSettings = false }

    val styleUrl = (state.mapStyle ?: if (dark) MapStyleOption.Night else MapStyleOption.Default).url
    val pin = directions?.destination?.takeIf { it.id != MY_LOCATION.id } ?: state.selected
    val route = nav?.route?.points ?: directions?.route?.points
    val exploring = directions == null && state.tab == Tab.Explore
    val showNav = nav == null && directions == null && state.selected == null

    Box(Modifier.fillMaxSize()) {
        RyMapView(
            styleUrl = styleUrl,
            route = route,
            selected = pin,
            location = state.location,
            hasPermission = state.hasPermission,
            following = state.following,
            commands = vm.camera,
            attributionBottomPx = sheetHeight,
            onTap = vm::onMapTap,
            onLongPress = vm::onMapLongPress,
            onBearing = { bearing = it },
            onFollowing = vm::setFollowing,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.tab == Tab.Saved && directions == null) {
            SavedScreen(state.saved, onOpen = vm::openSaved, onRemove = vm::removeSaved)
        }

        if (exploring) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) {
                SearchPill(
                    onClick = { haptics.click(); vm.openSearch(SearchTarget.Main) },
                    onAvatar = { haptics.click(); showSettings = true },
                    avatar = avatar,
                    name = settings.name,
                )
            }
            Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 84.dp, end = 16.dp), horizontalAlignment = Alignment.End) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                ) {
                    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { haptics.click(); vm.showLayers(true) }) { Symbol("layers") }
                        if (abs(bearing) > 1f) {
                            HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { haptics.tick(); vm.resetNorth() }) { Symbol("navigation", modifier = Modifier.rotate(-bearing)) }
                        }
                    }
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
                        AnimatedVisibility(
                            visible = !state.following,
                            enter = scaleIn(bouncySpring, initialScale = 0.6f) + fadeIn(),
                            exit = fadeOut() + scaleOut(bouncySpring, targetScale = 0.6f),
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = { haptics.click(); vm.follow() },
                                icon = { Symbol("navigation") },
                                text = { Text("Re-center") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else if (state.tab == Tab.Explore) {
                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn(bouncySpring, initialScale = 0.6f) + fadeIn(),
                            exit = fadeOut() + scaleOut(bouncySpring, targetScale = 0.6f),
                        ) {
                            SmallFloatingActionButton(
                                onClick = { haptics.click(); locate() },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (state.following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            ) { Symbol(if (state.following) "my_location" else "location_searching") }
                        }
                        if (directions == null) {
                            AnimatedVisibility(
                                visible = true,
                                enter = scaleIn(bouncySpring, initialScale = 0.6f) + fadeIn(),
                                exit = fadeOut() + scaleOut(bouncySpring, targetScale = 0.6f),
                            ) {
                                FloatingActionButton(
                                    onClick = { haptics.click(); vm.startDirections(state.selected) },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) { Symbol("directions", size = 28.dp) }
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().animateContentSize(
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ).onSizeChanged { sheetHeight = it.height },
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
                }
                if (showNav) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = state.tab == Tab.Explore,
                            onClick = { haptics.click(); vm.setTab(Tab.Explore) },
                            icon = { Symbol("explore") },
                            label = { Text("Explore") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { haptics.click(); vm.startDirections(null) },
                            icon = { Symbol("directions_car") },
                            label = { Text("Go") },
                        )
                        NavigationBarItem(
                            selected = state.tab == Tab.Saved,
                            onClick = { haptics.click(); vm.setTab(Tab.Saved) },
                            icon = { Symbol("bookmarks") },
                            label = { Text("Saved") },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.searchTarget != null,
            enter = scaleIn(bouncySpring, initialScale = 0.94f) + fadeIn(),
            exit = fadeOut() + scaleOut(bouncySpring, targetScale = 0.94f),
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
    if (showSettings) {
        SettingsScreen(
            settings = settings,
            avatar = avatar,
            recentsCount = state.recents.size,
            savedCount = state.saved.size,
            onDismiss = { showSettings = false },
            onName = vm::setName,
            onVoice = vm::setVoice,
            onTestVoice = vm::testVoice,
            onKeepScreenOn = vm::setKeepScreenOn,
            onUnits = vm::setUnits,
            onTheme = vm::setTheme,
            onPickPhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = vm::removeAvatar,
            onClearRecents = vm::clearRecents,
            onClearSaved = vm::clearSaved,
            onAbout = {
                showSettings = false
                showAbout = true
            },
        )
    }
    if (showAbout) AboutDialog { showAbout = false }
    if (showLocationPrompt) {
        AlertDialog(
            onDismissRequest = { showLocationPrompt = false },
            title = { Text("Location is off") },
            text = { Text("Before you continue, turn on location so RyMaps can find you and guide you turn by turn.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPrompt = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationPrompt = false }) { Text("Not now") }
            },
        )
    }
}
