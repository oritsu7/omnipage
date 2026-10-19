package com.ansh.omnipage.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.omnipage.reader.pdf.PdfSource
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Hairline
import com.ansh.omnipage.ui.theme.TextMuted
import kotlin.coroutines.cancellation.CancellationException

private const val THUMB_WIDTH_PX = 160

// Horizontal strip of page thumbnails shown in the reader's bottom bar; tap one to jump there. 
@Composable
fun PdfThumbnailStrip(
    source: PdfSource,
    currentPage: Int,
    onGo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val last = (source.pageCount - 1).coerceAtLeast(0)

    LaunchedEffect(currentPage) {
        state.animateScrollToItem((currentPage - 2).coerceIn(0, last))
    }

    LazyRow(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = source.pageCount, key = { it }) { index ->
            Thumb(source, index, selected = index == currentPage, onClick = { onGo(index) })
        }
    }
}

@Composable
private fun Thumb(source: PdfSource, index: Int, selected: Boolean, onClick: () -> Unit) {
    var bitmap by remember(source, index) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source, index) {
        try {
            bitmap = source.render(index, THUMB_WIDTH_PX).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // leave the blank thumbnail
        } catch (e: OutOfMemoryError) {
            // leave the blank thumbnail
        }
    }

    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Hairline, shape),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            "${index + 1}",
            color = if (selected) Accent else TextMuted,
            fontSize = 10.sp,
        )
    }
}
