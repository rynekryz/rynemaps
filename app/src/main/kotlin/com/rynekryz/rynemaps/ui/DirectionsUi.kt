@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.data.MY_LOCATION
import com.rynekryz.rynemaps.data.TravelMode
import com.rynekryz.rynemaps.ui.theme.Symbol
import com.rynekryz.rynemaps.util.formatDistance
import com.rynekryz.rynemaps.util.formatDuration
import java.util.Date

@Composable
private fun EndpointField(icon: String, text: String, placeholder: Boolean, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp).heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol(icon, size = 20.dp, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DirectionsTopCard(
    d: DirectionsState,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onEditOrigin: () -> Unit,
    onEditDestination: () -> Unit,
    onMode: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Symbol("arrow_back") }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val originMe = d.origin.id == MY_LOCATION.id
                    EndpointField(
                        icon = if (originMe) "my_location" else "trip_origin",
                        text = d.origin.name,
                        placeholder = false,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onEditOrigin,
                    )
                    EndpointField(
                        icon = "location_on",
                        text = d.destination?.name ?: "Choose destination",
                        placeholder = d.destination == null,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onEditDestination,
                    )
                }
                IconButton(onClick = onSwap) { Symbol("swap_vert") }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(TravelMode.entries.toList()) { mode ->
                    val selected = mode == d.mode
                    FilterChip(
                        selected = selected,
                        onClick = { onMode(mode) },
                        label = {
                            val r = d.route
                            Text(if (selected && r != null) "${mode.label} · ${formatDuration(r.duration)}" else mode.label)
                        },
                        leadingIcon = { Symbol(mode.icon, size = 18.dp) },
                    )
                }
            }
        }
    }
}

@Composable
fun DirectionsSheet(
    d: DirectionsState,
    canStart: Boolean,
    onStart: () -> Unit,
    onToggleSteps: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(shape = SheetShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            DragHandle()
            val r = d.route
            when {
                d.destination == null -> Text(
                    "Choose a destination to see routes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                d.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                d.error != null -> Column {
                    Text(d.error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
                r != null -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatDuration(r.duration),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            formatDistance(r.distance),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        "Fastest route by ${d.mode.label.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canStart) {
                            Button(onClick = onStart) {
                                Symbol("navigation", size = 20.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Start")
                            }
                        }
                        FilledTonalButton(onClick = onToggleSteps) {
                            Symbol(if (d.showSteps) "expand_more" else "list", size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Steps")
                        }
                    }
                    if (d.showSteps) {
                        Spacer(Modifier.size(12.dp))
                        HorizontalDivider()
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            itemsIndexed(r.steps) { _, step ->
                                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(step.icon)
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                                        if (step.distance > 0) {
                                            Text(
                                                formatDistance(step.distance),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationBanner(nav: NavState, modifier: Modifier = Modifier) {
    val step = nav.route.steps.getOrNull(nav.nextStep)
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol(
                if (nav.arrived) "sports_score" else step?.icon ?: "straight",
                size = 48.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                if (nav.arrived) {
                    Text("You have arrived", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(
                        formatDistance(nav.distanceToNext),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        if (nav.rerouting) "Rerouting…" else step?.text.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationBottomBar(nav: NavState, onExit: () -> Unit) {
    val context = LocalContext.current
    val eta = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (nav.remainingDuration * 1000).toLong()))
    Surface(shape = SheetShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatDuration(nav.remainingDuration),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${formatDistance(nav.remainingDistance)} · $eta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Symbol("close", size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Exit")
            }
        }
    }
}
