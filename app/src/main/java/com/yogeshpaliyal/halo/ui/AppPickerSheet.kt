package com.yogeshpaliyal.halo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<AppEntry>,
    selected: List<String>,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose favourites",
    /** Max items that can be ticked; null = no limit. */
    limit: Int? = Prefs.MAX_FAVORITES,
    /** Show each ticked item's position (favourites are ordered; other lists aren't). */
    showOrder: Boolean = true,
    countLabel: (Int) -> String = { "$it of ${Prefs.MAX_FAVORITES} picked" },
    /** Extra content under the title, e.g. a folder's name field. */
    header: (@Composable () -> Unit)? = null,
    /** First row of the list, above the apps (e.g. "New folder"). Hidden while searching. */
    leadingRow: (@Composable () -> Unit)? = null,
    /** Shows an edit button on folder rows. */
    onEditFolder: ((AppEntry) -> Unit)? = null,
    /** True while the app list is still being read; shows a spinner under what's ready. */
    loading: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val full = limit != null && selected.size >= limit

    // Keep the order stable while the sheet is open so rows don't jump as you tick them.
    val initialOrder = remember { selected.toList() }
    val filtered = remember(apps, query) {
        apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedBy { app -> initialOrder.indexOf(app.key).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (full) "All $limit slots used. Untick one to swap."
                        else countLabel(selected.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            if (header != null) {
                Spacer(Modifier.height(12.dp))
                header()
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            if (leadingRow != null && query.isBlank()) {
                item(key = "leading") { leadingRow() }
            }
            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
            items(filtered, key = { it.key }) { app ->
                val isSelected = app.key in selected
                val enabled = isSelected || !full
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onToggle(app) }
                        .alpha(if (enabled) 1f else 0.4f)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                    if (app.isWork) {
                        Spacer(Modifier.width(8.dp))
                        WorkTag(paused = !app.available)
                    }
                    if (app.action != null) {
                        Spacer(Modifier.width(8.dp))
                        ActionTag()
                    }
                    if (app.isFolder) {
                        Spacer(Modifier.width(8.dp))
                        FolderTag(app.children.size)
                    }
                    Spacer(Modifier.weight(1f))
                    if (app.isFolder && onEditFolder != null) {
                        IconButton(onClick = { onEditFolder(app) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit ${app.label}")
                        }
                    }
                    if (isSelected) {
                        if (showOrder) {
                            Text(
                                "${selected.indexOf(app.key) + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Folder tag for picker rows: "Folder · 3". */
@Composable
fun FolderTag(count: Int) {
    Text(
        "Folder · $count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** First row of the favourites picker: start a new folder. */
@Composable
fun NewFolderRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("New folder", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                if (enabled) "Group several apps into one slot" else "All slots used. Untick one to add a folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
