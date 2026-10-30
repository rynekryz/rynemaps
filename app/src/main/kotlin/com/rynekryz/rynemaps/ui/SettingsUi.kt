@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rynekryz.rynemaps.data.Settings
import com.rynekryz.rynemaps.data.ThemeMode
import com.rynekryz.rynemaps.data.UnitsMode
import com.rynekryz.rynemaps.ui.theme.Symbol

@Composable
private fun AvatarBadge(avatar: Bitmap?, size: androidx.compose.ui.unit.Dp, initials: String) {
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
        } else if (initials.isNotBlank()) {
            Text(initials, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    Row(modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ChipRow(label: String, options: List<String>, active: String, onSelect: (Int) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 76.dp, top = 8.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(start = 72.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = o == active,
                onClick = { onSelect(i) },
                label = { Text(o) },
            )
        }
    }
}

private fun initialsOf(name: String): String = name.trim().take(1).uppercase()

@Composable
fun SettingsScreen(
    settings: Settings,
    avatar: Bitmap?,
    recentsCount: Int,
    savedCount: Int,
    onDismiss: () -> Unit,
    onName: (String) -> Unit,
    onVoice: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onUnits: (UnitsMode) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onClearRecents: () -> Unit,
    onClearSaved: () -> Unit,
    onAbout: () -> Unit,
) {
    var editingName by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Symbol("arrow_back") }
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AvatarBadge(avatar, 88.dp, initialsOf(settings.name))
                    Text(
                        settings.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp).clickable { editingName = true },
                    )
                    Text(
                        "Stored on this device only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        IconButton(onClick = onPickPhoto) { Symbol("photo_camera") }
                        if (avatar != null) {
                            IconButton(onClick = onRemovePhoto) { Symbol("delete") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                SectionLabel("Navigation")
                SettingsRow(
                    "volume_up",
                    "Voice guidance",
                    subtitle = "Announce turns, reroutes and arrival",
                    trailing = { Switch(checked = settings.voice, onCheckedChange = onVoice) },
                )
                SettingsRow("graphic_eq", "Test voice", onClick = onTestVoice)
                SettingsRow(
                    "brightness_high",
                    "Keep screen on",
                    subtitle = "While navigating",
                    trailing = { Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOn) },
                )

                SectionLabel("Display")
                ChipRow("Theme", ThemeMode.entries.map { it.label }, settings.theme.label) { onTheme(ThemeMode.entries[it]) }

                SectionLabel("Distances")
                ChipRow("Units", UnitsMode.entries.map { it.label }, settings.units.label) { onUnits(UnitsMode.entries[it]) }

                SectionLabel("Privacy")
                SettingsRow("delete", "Clear search history", subtitle = if (recentsCount > 0) "$recentsCount saved" else "Empty", onClick = onClearRecents)
                SettingsRow("bookmark_remove", "Clear saved places", subtitle = if (savedCount > 0) "$savedCount saved" else "Empty", onClick = onClearSaved)

                SectionLabel("About")
                SettingsRow("info", "About RyMaps", onClick = onAbout)
                Text(
                    "RyMaps 1.1.1-beta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (editingName) {
        NameDialog(current = settings.name, onDismiss = { editingName = false }, onConfirm = {
            onName(it)
            editingName = false
        })
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                placeholder = { Text("Shown next to your photo") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
