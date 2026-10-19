package com.ansh.omnipage.ui.notes

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ansh.omnipage.OmniPageApp
import com.ansh.omnipage.data.db.AnnotationWithBook
import com.ansh.omnipage.ui.common.formatDate
import com.ansh.omnipage.ui.library.EmptyState
import com.ansh.omnipage.ui.reader.KIND_HIGHLIGHT
import com.ansh.omnipage.ui.reader.KIND_NOTE
import com.ansh.omnipage.ui.reader.KIND_QUOTE
import com.ansh.omnipage.ui.reader.NoteEditorDialog
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.CardBg
import com.ansh.omnipage.ui.theme.Night
import com.ansh.omnipage.ui.theme.Teal
import com.ansh.omnipage.ui.theme.TextMuted

private val FILTERS = listOf("All", "Highlights", "Quotes", "Notes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsScreen(
    onBack: () -> Unit,
    onOpen: (bookId: Long, locator: String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as OmniPageApp).container.reader }
    val vm: AnnotationsViewModel = viewModel(
        factory = viewModelFactory { initializer { AnnotationsViewModel(repo) } },
    )
    val items by vm.items.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<AnnotationWithBook?>(null) }
    var deleting by remember { mutableStateOf<AnnotationWithBook?>(null) }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                title = { Text("Quotes & Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FILTERS.forEachIndexed { i, label ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }

            val all = items
            val shown = all?.filter {
                when (filter) {
                    1 -> it.mark.kind == KIND_HIGHLIGHT
                    2 -> it.mark.kind == KIND_QUOTE
                    3 -> it.mark.kind == KIND_NOTE
                    else -> true
                }
            }

            when {
                shown == null -> Unit
                shown.isEmpty() -> EmptyState(
                    "Nothing here yet",
                    "Select text while reading and choose Highlight, Quote or Note. They all collect here.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.mark.id }) { item ->
                        AnnotationCard(
                            item = item,
                            onOpen = { onOpen(item.mark.bookId, item.mark.locator) },
                            onShare = { shareText(context, item) },
                            onEdit = { editing = item },
                            onDelete = { deleting = item },
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        NoteEditorDialog(
            title = "Edit note",
            quote = item.mark.quote,
            initial = item.mark.note,
            onDismiss = { editing = null },
            onSave = { vm.updateNote(item.mark.id, it); editing = null },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this ${labelOf(item.mark.kind).lowercase()}?") },
            text = { Text("It will be removed from “${item.bookTitle}”.") },
            confirmButton = { TextButton(onClick = { vm.delete(item.mark.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun labelOf(kind: String): String = when (kind) {
    KIND_HIGHLIGHT -> "Highlight"
    KIND_QUOTE -> "Quote"
    else -> "Note"
}

private fun iconOf(kind: String): ImageVector = when (kind) {
    KIND_HIGHLIGHT -> Icons.Outlined.Highlight
    KIND_QUOTE -> Icons.Outlined.FormatQuote
    else -> Icons.Outlined.Edit
}

@Composable
private fun AnnotationCard(
    item: AnnotationWithBook,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val mark = item.mark
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconOf(mark.kind), null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.padding(start = 6.dp))
                Text(labelOf(mark.kind), color = Accent, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            if (mark.quote.isNotEmpty()) {
                Text(mark.quote, fontStyle = FontStyle.Italic, maxLines = 8, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            if (mark.note.isNotEmpty()) {
                Text(mark.note, maxLines = 8, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                item.bookTitle,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${mark.label} · ${formatDate(mark.createdAt)}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Share", tint = TextMuted) }
                if (mark.kind == KIND_NOTE) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit note", tint = TextMuted) }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = TextMuted) }
            }
        }
    }
}

private fun shareText(context: Context, item: AnnotationWithBook) {
    val text = buildString {
        if (item.mark.quote.isNotEmpty()) append("“").append(item.mark.quote).append("”\n")
        if (item.mark.note.isNotEmpty()) append(item.mark.note).append('\n')
        append("— ").append(item.bookTitle)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share")) }
}