package com.ansh.omnipage.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ansh.omnipage.OmniPageApp
import com.ansh.omnipage.data.bookFormat
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.ui.common.formatDate
import com.ansh.omnipage.ui.common.formatSize
import com.ansh.omnipage.ui.common.shareBook
import com.ansh.omnipage.ui.library.BookCover
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Night
import com.ansh.omnipage.ui.theme.Teal
import com.ansh.omnipage.ui.theme.TextMuted
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(bookId: Long, onBack: () -> Unit, onRead: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as OmniPageApp).container.repository }
    val vm: DetailViewModel = viewModel(
        key = "detail_$bookId",
        factory = viewModelFactory { initializer { DetailViewModel(bookId, repo) } },
    )
    val book by vm.book.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val allCollections by vm.collections.collectAsStateWithLifecycle()
    val memberIds by vm.memberIds.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                title = { Text("About Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = onRead) {
                        Text("READ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
            )
        },
    ) { padding ->
        val b = book
        if (b != null) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                BookCover(b, Modifier.fillMaxWidth(0.68f).aspectRatio(2f / 3f))
                Spacer(Modifier.height(20.dp))
                Text(b.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActionIcon(
                        if (b.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        "Favorite", b.isFavorite, vm::toggleFavorite,
                    )
                    ActionIcon(Icons.Outlined.Schedule, "To read", b.isToRead, vm::toggleToRead)
                    ActionIcon(Icons.Outlined.DoneAll, "Have read", b.isFinished, vm::toggleFinished)
                    ActionIcon(Icons.Outlined.CollectionsBookmark, "Collections", memberIds.isNotEmpty()) {
                        picking = true
                    }
                    ActionIcon(Icons.Outlined.Share, "Share", false) { shareBook(context, b) }
                    if (b.trashed) {
                        ActionIcon(Icons.Outlined.RestoreFromTrash, "Restore", false, vm::restore)
                    } else {
                        ActionIcon(Icons.Outlined.Delete, "Move to Trash", false) { vm.trash(onBack) }
                    }
                    ActionIcon(Icons.Outlined.Edit, "Edit details", false) { editing = true }
                }
                Spacer(Modifier.height(18.dp))

                if (b.author.isNotBlank()) InfoRow(b.author, "Author")
                if (b.series.isNotBlank()) {
                    val shelfNames = allCollections.filter { it.id in memberIds }.joinToString(", ") { it.name }
                    if (shelfNames.isNotEmpty()) InfoRow(shelfNames, "Collections")
                    val index = if (b.seriesIndex > 0f) " #${trimFloat(b.seriesIndex)}" else ""
                    InfoRow(b.series + index, "Series")
                }
                InfoRow("${b.bookFormat?.label ?: b.format}, ${formatSize(b.sizeBytes)}", "Format")
                if (b.pageCount > 0) InfoRow("${b.pageCount}", "Pages")
                if (b.progress > 0f) InfoRow("${(b.progress * 100).roundToInt()}%", "Read")
                InfoRow(if (b.relPath.isNotBlank()) b.relPath else b.fileName, "Location")
                InfoRow(formatDate(b.addedAt), "Added")
                if (b.lastOpenedAt > 0) InfoRow(formatDate(b.lastOpenedAt), "Last opened")
            }

            if (picking) {
                com.ansh.omnipage.ui.library.CollectionPickerDialog(
                    bookTitle = b.title,
                    collections = allCollections,
                    memberIds = memberIds,
                    onToggle = { collection, member -> vm.setMembership(collection, member) },
                    onCreate = vm::createCollectionAndAdd,
                    onDismiss = { picking = false },
                )
            }

            if (editing) EditDialog(b, onDismiss = { editing = false }) { t, a, s ->
                vm.edit(t, a, s)
                editing = false
            }
        }
    }
}

private fun trimFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

@Composable
private fun ActionIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, description, tint = if (active) Accent else TextMuted, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun InfoRow(value: String, label: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Composable
private fun EditDialog(book: BookEntity, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true)
                OutlinedTextField(series, { series = it }, label = { Text("Series") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, author, series) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
