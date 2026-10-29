@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.data.Categories
import com.rynekryz.rynemaps.data.Category
import com.rynekryz.rynemaps.data.Place
import com.rynekryz.rynemaps.ui.theme.Symbol

@Composable
fun SearchPill(onClick: () -> Unit, onAvatar: () -> Unit, avatar: android.graphics.Bitmap?, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol("search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(
                "Search here",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onAvatar),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("R", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun CategoryChips(active: Category?, onSelect: (Category) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Categories) { cat ->
            ElevatedFilterChip(
                selected = active?.id == cat.id,
                onClick = { onSelect(cat) },
                label = { Text(cat.label) },
                leadingIcon = { Symbol(cat.icon, size = 18.dp) },
                colors = FilterChipDefaults.elevatedFilterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
fun PlaceSheet(
    place: Place,
    saved: Boolean,
    loading: Boolean,
    onClose: () -> Unit,
    onDirections: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(shape = SheetShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            DragHandle()
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                    if (place.subtitle.isNotBlank()) {
                        Text(
                            place.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                FilledTonalIconButton(onClick = onClose) { Symbol("close") }
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDirections) {
                    Symbol("directions", size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Directions")
                }
                FilledTonalButton(onClick = onSave) {
                    Symbol(if (saved) "bookmark_added" else "bookmark_add", size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saved) "Saved" else "Save")
                }
                FilledTonalIconButton(onClick = onShare) { Symbol("share") }
            }
            if (place.address.isNotBlank() || loading) {
                Spacer(Modifier.size(16.dp))
                HorizontalDivider()
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Symbol("location_on", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    if (place.address.isNotBlank()) {
                        Text(place.address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun ResultsSheet(
    category: Category,
    results: List<Place>,
    loading: Boolean,
    onClose: () -> Unit,
    onSelect: (Place) -> Unit,
) {
    Surface(shape = SheetShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding()) {
            DragHandle()
            Row(
                Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(category.icon, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(category.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onClose) { Symbol("close") }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                results.isEmpty() -> Text(
                    "No results in this area",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { p ->
                        PlaceRow(p.icon, p.name, p.address.ifBlank { p.subtitle }, onClick = { onSelect(p) })
                    }
                }
            }
        }
    }
}

fun sharePlace(context: Context, place: Place) {
    val lat = place.point.lat
    val lon = place.point.lon
    val url = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
    val text = buildString {
        append(place.name)
        if (place.address.isNotBlank()) append("\n").append(place.address)
        append("\n").append(url)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
