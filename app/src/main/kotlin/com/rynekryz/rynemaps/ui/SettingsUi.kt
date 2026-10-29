@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.data.Settings
import com.rynekryz.rynemaps.data.ThemeMode
import com.rynekryz.rynemaps.data.UnitsMode
import com.rynekryz.rynemaps.ui.theme.Symbol

@Composable
private fun AvatarBadge(avatar: Bitmap?, size: Dp, initials: String = "R") {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
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
            Text(initials, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ChipRow(label: String, options: List<String>, active: String, onSelect: (Int) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 72.dp, top = 10.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(start = 72.dp, bottom = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = o == active,
                onClick = { onSelect(i) },
                label = { Text(o) },
            )
        }
    }
}

@Composable
fun SettingsSheet(
    settings: Settings,
    avatar: Bitmap?,
    recentsCount: Int,
    onDismiss: () -> Unit,
    onVoice: (Boolean) -> Unit,
    onUnits: (UnitsMode) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onClearRecents: () -> Unit,
    onAbout: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
                .padding(bottom = 20.dp)
                .heightIn(max = 520.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(avatar, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ryne", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Stored on this device only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPickPhoto) { Symbol("photo_camera") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            if (avatar != null) {
                SettingsRow("delete", "Remove profile photo", onClick = onRemovePhoto)
            }
            SettingsRow(
                "volume_up",
                "Voice guidance",
                subtitle = "Turn-by-turn navigation announcements",
                trailing = { Switch(checked = settings.voice, onCheckedChange = onVoice) },
            )
            ChipRow(
                "Distance units",
                UnitsMode.entries.map { it.label },
                settings.units.label,
            ) { onUnits(UnitsMode.entries[it]) }
            ChipRow(
                "Theme",
                ThemeMode.entries.map { it.label },
                settings.theme.label,
            ) { onTheme(ThemeMode.entries[it]) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsRow("delete", "Clear search history", subtitle = if (recentsCount > 0) "$recentsCount saved" else "Nothing saved", onClick = onClearRecents)
            SettingsRow("info", "About RyMaps", onClick = onAbout)
            Text(
                "RyMaps 1.1.0-beta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
            )
        }
    }
}
