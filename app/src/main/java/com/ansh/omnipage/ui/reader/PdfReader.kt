package com.ansh.omnipage.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ansh.omnipage.data.reader.ReaderSettings
import com.ansh.omnipage.data.reader.ReaderTheme
import com.ansh.omnipage.reader.pdf.PdfSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.min

class PdfController {
    internal var goTo: ((Int) -> Unit)? = null

    fun goToPage(page: Int) {
        goTo?.invoke(page)
    }
}

private const val MAX_RENDER_WIDTH = 2200
private const val MAX_ZOOM = 5f

/**
 * Repaints a rendered page in the reader theme: white paper becomes the theme background and black ink
 * becomes the theme text colour (out = fg + (bg - fg) * src, per channel). This is what makes Sepia look
 * sepia on the page itself, and gives each dark theme its own colours instead of a plain inversion.
 */
private fun themeFilter(theme: ReaderTheme): ColorFilter {
    fun ch(color: Long, shift: Int): Float = ((color shr shift) and 0xFF).toInt() / 255f
    val bgR = ch(theme.bg, 16); val bgG = ch(theme.bg, 8); val bgB = ch(theme.bg, 0)
    val fgR = ch(theme.fg, 16); val fgG = ch(theme.fg, 8); val fgB = ch(theme.fg, 0)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                bgR - fgR, 0f, 0f, 0f, fgR * 255f,
                0f, bgG - fgG, 0f, 0f, fgG * 255f,
                0f, 0f, bgB - fgB, 0f, fgB * 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

private fun renderWidthFor(viewportPx: Float): Int =
    min(MAX_RENDER_WIDTH, (ceil(viewportPx / 128f).toInt() * 128).coerceAtLeast(128))

@Composable
fun PdfReader(
    source: PdfSource,
    settings: ReaderSettings,
    startPage: Int,
    controller: PdfController,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = settings.pdfInvert
    val filter = remember(settings.theme) { themeFilter(settings.theme) }
    val placeholder = if (tint) Color(settings.theme.bg) else Color.White
    val latestOnPage by rememberUpdatedState(onPage)
    val latestToggle by rememberUpdatedState(onToggleChrome)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        if (settings.pdfPaged) {
            PagedPdf(
                source = source,
                filter = if (tint) filter else null,
                placeholder = placeholder,
                viewportW = viewportW,
                startPage = startPage,
                tapZones = settings.tapZones,
                controller = controller,
                onPage = { latestOnPage(it) },
                onToggleChrome = { latestToggle() },
            )
        } else {
            ContinuousPdf(
                source = source,
                filter = if (tint) filter else null,
                placeholder = placeholder,
                viewportW = viewportW,
                viewportH = viewportH,
                startPage = startPage,
                tapZones = settings.tapZones,
                controller = controller,
                onPage = { latestOnPage(it) },
                onToggleChrome = { latestToggle() },
            )
        }
    }
}


// Continuous scroll (zoom = wider page + horizontal pan)
@Composable
private fun ContinuousPdf(
    source: PdfSource,
    filter: ColorFilter?,
    placeholder: Color,
    viewportW: Int,
    viewportH: Int,
    startPage: Int,
    tapZones: Boolean,
    controller: PdfController,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val hScroll = rememberScrollState()
    var zoom by rememberSaveable { mutableStateOf(1f) }
    val zoomBy = rememberUpdatedState<(Float) -> Unit> { factor -> zoom = (zoom * factor).coerceIn(1f, 3f) }

    val widthPx = viewportW * zoom
    val renderWidth = renderWidthFor(widthPx)

    DisposableEffect(controller, listState) {
        controller.goTo = { page ->
            scope.launch { listState.scrollToItem(page.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))) }
        }
        onDispose { controller.goTo = null }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index
                ?: listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { onPage(it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Pinch to zoom: watched on the Initial pass, and only consumed while two fingers are down.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) {
                                zoomBy.value(z)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(tapZones) {
                detectTapGestures(
                    onDoubleTap = {
                        zoom = if (zoom > 1.05f) 1f else 2f
                        if (zoom == 1f) scope.launch { hScroll.scrollTo(0) }
                    },
                    onTap = { offset ->
                        val x = offset.x / size.width
                        when {
                            tapZones && x < 0.25f -> scope.launch { listState.animateScrollBy(-viewportH * 0.9f) }
                            tapZones && x > 0.75f -> scope.launch { listState.animateScrollBy(viewportH * 0.9f) }
                            else -> onToggleChrome()
                        }
                    },
                )
            }
            .horizontalScroll(hScroll),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = source.pageCount, key = { it }) { index ->
                PdfPageView(
                    source = source,
                    index = index,
                    renderWidth = renderWidth,
                    filter = filter,
                    placeholder = placeholder,
                    fit = false,
                )
            }
        }
    }
}


// Single page (swipe between pages; each page pinch-zooms, double-taps and pans on its own)
@Composable
private fun PagedPdf(
    source: PdfSource,
    filter: ColorFilter?,
    placeholder: Color,
    viewportW: Int,
    startPage: Int,
    tapZones: Boolean,
    controller: PdfController,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lastPage = (source.pageCount - 1).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, lastPage)) { source.pageCount }

    DisposableEffect(controller, pagerState) {
        controller.goTo = { page -> scope.launch { pagerState.scrollToPage(page.coerceIn(0, lastPage)) } }
        onDispose { controller.goTo = null }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { onPage(it) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { index ->
        // Re-render at a higher resolution once a zoom gesture settles, so zoomed text stays sharp.
        var renderScale by remember(index) { mutableFloatStateOf(1f) }
        ZoomableBox(
            modifier = Modifier.fillMaxSize(),
            onSettled = { s -> renderScale = if (s > 2.2f) 3f else if (s > 1.1f) 2f else 1f },
            onTap = { x, zoomed ->
                when {
                    !zoomed && tapZones && x < 0.28f -> scope.launch {
                        if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                    !zoomed && tapZones && x > 0.72f -> scope.launch {
                        if (pagerState.currentPage < lastPage) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                    else -> onToggleChrome()
                }
            },
        ) {
            PdfPageView(
                source = source,
                index = index,
                renderWidth = renderWidthFor(viewportW * renderScale),
                filter = filter,
                placeholder = placeholder,
                fit = true,
            )
        }
    }
}

/**
 Pinch / double-tap zoom with panning. With one finger at 1x it does not consume anything, so the
 parent pager still handles page swipes; once zoomed (or with two fingers) it keeps the gesture.
 */
@Composable
private fun ZoomableBox(
    modifier: Modifier = Modifier,
    onSettled: (Float) -> Unit,
    onTap: (xFraction: Float, zoomed: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSettled by rememberUpdatedState(onSettled)
    val latestTap by rememberUpdatedState(onTap)

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            latestSettled(1f)
                        } else {
                            val target = 2.5f
                            val c = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            scale = target
                            offset = clamp((pos - c) * (1f - target), target) // keep the tapped point under the finger
                            latestSettled(target)
                        }
                    },
                    onTap = { pos -> latestTap(pos.x / boxSize.width.coerceAtLeast(1), scale > 1.05f) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1.01f) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                            if (centroid.isSpecified) {
                                val c = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                val f = centroid - c
                                val ratio = newScale / scale
                                offset = clamp(f - (f - offset) * ratio + pan, newScale)
                            }
                            scale = newScale
                            moved = true
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (moved) latestSettled(scale)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}


// One rendered page
@Composable
private fun PdfPageView(
    source: PdfSource,
    index: Int,
    renderWidth: Int,
    filter: ColorFilter?,
    placeholder: Color,
    fit: Boolean,
) {
    var bitmap by remember(source, index) { mutableStateOf<ImageBitmap?>(null) }
    var ratio by remember(source, index) {
        mutableFloatStateOf(source.knownRatio(index).takeIf { it > 0f } ?: 1.414f)
    }

    LaunchedEffect(source, index) {
        try {
            ratio = source.ratioOf(index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // keep the estimate
        }
    }
    LaunchedEffect(source, index, renderWidth) {
        try {
            bitmap = source.render(index, renderWidth).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // leave the placeholder
        } catch (e: OutOfMemoryError) {
            // leave the placeholder
        }
    }

    val frame = if (fit) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / ratio)
            .background(placeholder)
    }

    Box(frame, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (fit) ContentScale.Fit else ContentScale.FillBounds,
                colorFilter = filter,
            )
        }
    }
}
