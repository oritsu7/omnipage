package com.ansh.omnipage.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ansh.omnipage.data.bookFormat
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.ui.common.formatSize
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.CardBg
import com.ansh.omnipage.ui.theme.Hairline
import com.ansh.omnipage.ui.theme.TextMuted
import java.io.File
import kotlin.math.abs

/**
 Everything a book card can do; shared by list and grid items.
 Created ONCE per screen and takes the book as an argument. A new instance per row (the old design)
 made every row recompose whenever anything on the screen changed.
*/
@Stable
class BookActions(
    val onOpen: (BookEntity) -> Unit,
    val onAbout: (BookEntity) -> Unit,
    val onFavorite: (BookEntity) -> Unit,
    val onToRead: (BookEntity) -> Unit,
    val onFinished: (BookEntity) -> Unit,
    val onCollections: (BookEntity) -> Unit,
    val onShare: (BookEntity) -> Unit,
    val onTrash: (BookEntity) -> Unit,
    val onRestore: (BookEntity) -> Unit,
)

private val placeholderColors = listOf(
    Color(0xFF1F4E5F), Color(0xFF5A3A2E), Color(0xFF3B4A6B),
    Color(0xFF4A5D3A), Color(0xFF6B3B4F), Color(0xFF4F4A2E),
)

@Composable
fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    val color = remember(book.title) { placeholderColors[abs(book.title.hashCode()) % placeholderColors.size] }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    ) {
        val path = book.coverPath
        if (path != null) {
            val context = LocalContext.current
            // Built once per cover file instead of on every recomposition.
            val request = remember(path) {
                ImageRequest.Builder(context).data(File(path)).crossfade(false).build()
            }
            AsyncImage(
                model = request,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.bookFormat?.label.orEmpty(),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                )
                Text(
                    book.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Thin track with a filled portion and a dot at the reading position, as in the reference. 
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        if (p > 0f) {
            drawLine(Accent, Offset(0f, y), Offset(size.width * p, y), strokeWidth = stroke)
        }
        val dotX = (size.width * p).coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())
        drawCircle(if (p > 0f) Accent else TextMuted, radius = 3.dp.toPx(), center = Offset(dotX, y))
    }
}

private fun subtitle(book: BookEntity): String =
    "${book.bookFormat?.label ?: book.format}, ${formatSize(book.sizeBytes)}"

@Composable
private fun BookMenu(onDismiss: () -> Unit, book: BookEntity, actions: BookActions) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("About document") }, onClick = { onDismiss(); actions.onAbout(book) })
        DropdownMenuItem(text = { Text("Share") }, onClick = { onDismiss(); actions.onShare(book) })
        if (book.trashed) {
            DropdownMenuItem(text = { Text("Restore") }, onClick = { onDismiss(); actions.onRestore(book) })
        } else {
            DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { onDismiss(); actions.onTrash(book) })
        }
    }
}

@Composable
private fun CardIcon(icon: ImageVector, active: Boolean, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) Accent else TextMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun BookRow(book: BookEntity, actions: BookActions) {
    var menu by remember { mutableStateOf(false) }
    val subtitle = remember(book.format, book.sizeBytes) { subtitle(book) }
    Card(
        onClick = { actions.onOpen(book) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            BookCover(book, Modifier.width(84.dp).height(124.dp))
            Column(
                Modifier
                    .weight(1f)
                    .height(124.dp)
                    .padding(start = 14.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.weight(1f))
                ProgressLine(book.progress)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (book.trashed) {
                        TextButton(onClick = { actions.onRestore(book) }) { Text("Restore") }
                    } else {
                        CardIcon(
                            if (book.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            book.isFavorite, "Favorite",
                        ) { actions.onFavorite(book) }
                        CardIcon(Icons.Outlined.Schedule, book.isToRead, "To read") { actions.onToRead(book) }
                        CardIcon(Icons.Outlined.DoneAll, book.isFinished, "Have read") { actions.onFinished(book) }
                        CardIcon(Icons.Outlined.CollectionsBookmark, false, "Collections") { actions.onCollections(book) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Outlined.MoreVert, "More", tint = TextMuted, modifier = Modifier.size(22.dp))
                        }
                        // Only composed while open: no menu machinery for the hundreds of closed ones.
                        if (menu) BookMenu({ menu = false }, book, actions)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(book: BookEntity, actions: BookActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = { actions.onOpen(book) }, onLongClick = { menu = true }),
        ) {
            Box {
                BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                if (book.isFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = Accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            ProgressLine(book.progress)
        }
        if (menu) BookMenu({ menu = false }, book, actions)
    }
}