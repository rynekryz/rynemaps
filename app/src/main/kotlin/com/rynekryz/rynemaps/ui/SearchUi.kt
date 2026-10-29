@file:OptIn(ExperimentalMaterial3Api::class)

package com.rynekryz.rynemaps.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.rynekryz.rynemaps.data.Place
import com.rynekryz.rynemaps.ui.theme.Symbol

@Composable
fun SearchOverlay(
    state: UiState,
    target: SearchTarget,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onPick: (Place) -> Unit,
    onMyLocation: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Symbol("arrow_back") }
                TextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onQuery(it)
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = {
                        Text(
                            when (target) {
                                SearchTarget.Main -> "Search here"
                                SearchTarget.Origin -> "Choose starting point"
                                SearchTarget.Destination -> "Choose destination"
                            }
                        )
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = {
                                text = ""
                                onQuery("")
                            }) { Symbol("close") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        state.results.firstOrNull()?.let(onPick)
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (text.isBlank()) {
                    if (target != SearchTarget.Main) {
                        item {
                            PlaceRow("my_location", "Your location", "", onClick = onMyLocation)
                        }
                    }
                    items(state.recents, key = { "r" + it.id }) { p ->
                        PlaceRow("history", p.name, p.address.ifBlank { p.subtitle }, onClick = { onPick(p) })
                    }
                } else {
                    if (state.searching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(state.results, key = { it.id }) { p ->
                        PlaceRow(p.icon, p.name, p.subtitle, onClick = { onPick(p) })
                    }
                    if (!state.searching && state.results.isEmpty()) {
                        item {
                            Text(
                                if (state.searchFailed) "Search is unavailable right now. Check your connection." else "No results",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedScreen(saved: List<Place>, onOpen: (Place) -> Unit, onRemove: (Place) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Text(
                "Saved",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp),
            )
            if (saved.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp).navigationBarsPadding().padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Symbol("bookmarks", size = 64.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Places you save will show up here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(saved, key = { it.id }) { p ->
                        PlaceRow(
                            p.icon, p.name, p.address.ifBlank { p.subtitle },
                            onClick = { onOpen(p) },
                            trailing = {
                                IconButton(onClick = { onRemove(p) }) {
                                    Symbol("bookmark_added", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
