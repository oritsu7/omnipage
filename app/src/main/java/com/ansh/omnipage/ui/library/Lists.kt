package com.ansh.omnipage.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ansh.omnipage.data.BookFormat
import com.ansh.omnipage.data.Section
import com.ansh.omnipage.data.db.FolderWithCount
import com.ansh.omnipage.data.db.GroupCount
import com.ansh.omnipage.ui.common.formatDate
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Hairline
import com.ansh.omnipage.ui.theme.TextMuted

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
            if (actions != null) {
                Spacer(Modifier.height(20.dp))
                actions()
            }
        }
    }
}

fun emptyCopy(section: Section, searching: Boolean): Pair<String, String> =
    if (searching) {
        "No matches" to "Try a different title or author."
    } else {
        when (section) {
            Section.READING_NOW -> "Nothing in progress" to "Books you open will show up here."
            Section.FAVORITES -> "No favorites yet" to "Tap the star on a book to keep it here."
            Section.TO_READ -> "Your to-read list is empty" to "Tap the clock on a book to queue it."
            Section.HAVE_READ -> "No finished books" to "Tap the double check on a book to mark it read."
            Section.TRASH -> "Trash is empty" to "Books you move to Trash wait here until you restore them."
            Section.DOWNLOADS -> "No downloads found" to "Books inside a Download folder you've added show up here."
            else -> "Your library is empty" to "Add a folder to scan, or pick individual files."
        }
    }

@Composable
fun AddSourceButtons(onAddFolder: () -> Unit, onAddFiles: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = onAddFolder) {
            Icon(Icons.Outlined.CreateNewFolder, null)
            Spacer(Modifier.padding(start = 8.dp))
            Text("Add folder")
        }
        OutlinedButton(onClick = onAddFiles) {
            Icon(Icons.Outlined.NoteAdd, null)
            Spacer(Modifier.padding(start = 8.dp))
            Text("Add files")
        }
    }
}

// Authors / Series / Formats index. 
@Composable
fun GroupList(section: Section, groups: List<GroupCount>, onOpen: (GroupCount) -> Unit) {
    if (groups.isEmpty()) {
        val what = when (section) {
            Section.AUTHORS -> "No authors yet" to "Books with author metadata (EPUB, DOCX) are listed here."
            Section.SERIES -> "No series yet" to "EPUBs tagged with a series are grouped here."
            else -> "Nothing here yet" to "Add books to see them grouped."
        }
        EmptyState(what.first, what.second)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.name }) { g ->
            val label = if (section == Section.FORMATS) BookFormat.fromName(g.name)?.label ?: g.name else g.name
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = { Text("${g.count} ${if (g.count == 1) "book" else "books"}", color = TextMuted) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextMuted) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(g) },
            )
            HorizontalDivider(color = Hairline)
        }
    }
}

@Composable
fun FolderList(
    folders: List<FolderWithCount>,
    scanning: Boolean,
    onOpen: (FolderWithCount) -> Unit,
    onRescan: (FolderWithCount) -> Unit,
    onRemove: (FolderWithCount) -> Unit,
    onAddFolder: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<FolderWithCount?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onAddFolder) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("Add folder")
                }
            }
        }
        if (folders.isEmpty()) {
            item {
                Text(
                    "No folders yet. Add one and OmniPage keeps it in sync automatically.",
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(folders, key = { it.folder.id }) { item ->
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Folder, null, tint = Accent) },
                headlineContent = { Text(item.folder.displayName) },
                supportingContent = {
                    val scanned = if (item.folder.lastScanAt > 0) "scanned ${formatDate(item.folder.lastScanAt)}" else "not scanned yet"
                    Text("${item.bookCount} ${if (item.bookCount == 1) "book" else "books"}, $scanned", color = TextMuted)
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onRescan(item) }, enabled = !scanning) {
                            Icon(Icons.Outlined.Refresh, "Rescan", tint = TextMuted)
                        }
                        IconButton(onClick = { pendingRemoval = item }) {
                            Icon(Icons.Outlined.Delete, "Remove folder", tint = TextMuted)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(item) },
            )
            HorizontalDivider(color = Hairline)
        }
    }

    val target = pendingRemoval
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove folder?") },
            text = {
                Text(
                    "“${target.folder.displayName}” and its ${target.bookCount} books leave the library, " +
                        "along with their reading progress. The files on your device stay untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(target); pendingRemoval = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}
