package com.ansh.omnipage.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ansh.omnipage.data.db.CollectionEntity
import com.ansh.omnipage.data.db.CollectionWithCount
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Hairline
import com.ansh.omnipage.ui.theme.TextMuted

// The "Collections" drawer section: all shelves, with create / rename / delete. 
@Composable
fun CollectionList(
    collections: List<CollectionWithCount>,
    onOpen: (CollectionWithCount) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (CollectionWithCount, String) -> Unit,
    onDelete: (CollectionWithCount) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CollectionWithCount?>(null) }
    var deleting by remember { mutableStateOf<CollectionWithCount?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("New collection")
                }
            }
        }
        if (collections.isEmpty()) {
            item {
                Text(
                    "No collections yet. Create a shelf, then use the collections button on a book to file it.",
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(collections, key = { it.collection.id }) { item ->
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CollectionsBookmark, null, tint = Accent) },
                headlineContent = { Text(item.collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${item.bookCount} ${if (item.bookCount == 1) "book" else "books"}", color = TextMuted)
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { renaming = item }) {
                            Icon(Icons.Outlined.Edit, "Rename", tint = TextMuted)
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Outlined.Delete, "Delete collection", tint = TextMuted)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(item) },
            )
            HorizontalDivider(color = Hairline)
        }
    }

    if (creating) {
        NameDialog("New collection", "", "Create", { creating = false }) {
            onCreate(it)
            creating = false
        }
    }
    renaming?.let { target ->
        NameDialog("Rename collection", target.collection.name, "Save", { renaming = null }) {
            onRename(target, it)
            renaming = null
        }
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete collection?") },
            text = { Text("“${target.collection.name}” is removed. The books in it stay in your library.") },
            confirmButton = { TextButton(onClick = { onDelete(target); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// Tick the shelves a book belongs to; changes apply immediately. 
@Composable
fun CollectionPickerDialog(
    bookTitle: String,
    collections: List<CollectionEntity>,
    memberIds: Set<Long>,
    onToggle: (CollectionEntity, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to collection") },
        text = {
            Column {
                Text(bookTitle, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (collections.isEmpty()) {
                    Text("No collections yet. Create your first one.", color = TextMuted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(collections, key = { it.id }) { c ->
                            val checked = c.id in memberIds
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(c, !checked) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { onToggle(c, it) })
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { creating = true }) { Text("New collection") } },
    )

    if (creating) {
        NameDialog("New collection", "", "Create", { creating = false }) {
            onCreate(it)
            creating = false
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}