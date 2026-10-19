package com.ansh.omnipage.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ansh.omnipage.data.db.BookmarkEntity
import com.ansh.omnipage.data.reader.ReaderFont
import com.ansh.omnipage.data.reader.ReaderSettings
import com.ansh.omnipage.data.reader.ReaderTheme
import com.ansh.omnipage.reader.reflow.TocItem
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Hairline
import com.ansh.omnipage.ui.theme.Panel
import com.ansh.omnipage.ui.theme.TextHigh
import com.ansh.omnipage.ui.theme.TextMuted
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt


// Settings
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: ReaderSettings,
    isPdf: Boolean,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        contentColor = TextHigh,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Reading settings", style = MaterialTheme.typography.titleMedium)

            Label("Theme")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ReaderTheme.entries.forEach { theme ->
                    ThemeSwatch(theme, selected = theme == settings.theme) {
                        onChange { s -> s.copy(theme = theme) }
                    }
                }
            }

            if (!isPdf) {
                Label("Font")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFont.entries.forEach { font ->
                        FilterChip(
                            selected = font == settings.font,
                            onClick = { onChange { s -> s.copy(font = font) } },
                            label = { Text(font.label) },
                        )
                    }
                }

                ValueSlider("Text size", "${settings.fontSize}", settings.fontSize.toFloat(), 12f..34f, 21) {
                    onChange { s -> s.copy(fontSize = it.roundToInt()) }
                }
                ValueSlider("Line spacing", "%.1f".format(settings.lineHeight), settings.lineHeight, 1.2f..2.2f, 9) {
                    onChange { s -> s.copy(lineHeight = (it * 10f).roundToInt() / 10f) }
                }
                ValueSlider("Margins", "${settings.margin}", settings.margin.toFloat(), 0f..48f, 23) {
                    onChange { s -> s.copy(margin = it.roundToInt()) }
                }

                ToggleRow("Page turning", "Off = continuous scroll", settings.paged) {
                    onChange { s -> s.copy(paged = it) }
                }
                ToggleRow("Justify text", null, settings.justify) { onChange { s -> s.copy(justify = it) } }
            } else {
                ToggleRow("Single page view", "Off = continuous scroll", settings.pdfPaged) {
                    onChange { s -> s.copy(pdfPaged = it) }
                }
                ToggleRow("Use theme colours on pages", "Off = original white pages", settings.pdfInvert) {
                    onChange { s -> s.copy(pdfInvert = it) }
                }
            }

            HorizontalDivider(color = Hairline)

            ToggleRow("Tap screen edges to turn pages", null, settings.tapZones) {
                onChange { s -> s.copy(tapZones = it) }
            }
            ToggleRow("Keep screen on", null, settings.keepScreenOn) { onChange { s -> s.copy(keepScreenOn = it) } }

            ToggleRow("Match system brightness", null, settings.brightness < 0f) { system ->
                onChange { s -> s.copy(brightness = if (system) -1f else 0.6f) }
            }
            if (settings.brightness >= 0f) {
                ValueSlider("Brightness", "${(settings.brightness * 100).roundToInt()}%", settings.brightness, 0.05f..1f, 0) {
                    onChange { s -> s.copy(brightness = it) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextMuted, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ThemeSwatch(theme: ReaderTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(theme.bg))
                .border(if (selected) 3.dp else 1.dp, if (selected) Accent else TextMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = Color(theme.fg), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            theme.label,
            color = if (selected) Accent else TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ValueSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
) {
    var live by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, color = Accent)
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onCommit(live) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.35f)),
        )
    }
}


// Contents / bookmarks / quotes
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    isPdf: Boolean,
    pageCount: Int,
    currentPage: Int,
    toc: List<TocItem>,
    currentChapter: Int,
    marks: List<BookmarkEntity>,
    onGoToPage: (Int) -> Unit,
    onOpenToc: (TocItem) -> Unit,
    onOpenMark: (BookmarkEntity) -> Unit,
    onDeleteMark: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val bookmarks = marks.filter { it.kind == KIND_BOOKMARK }
    val highlights = marks.filter { it.kind == KIND_HIGHLIGHT }
    val quotes = marks.filter { it.kind == KIND_QUOTE }
    val notes = marks.filter { it.kind == KIND_NOTE }

    // Tab ids: 0 contents/pages, 1 bookmarks, 2 highlights, 3 quotes, 4 notes.
    // PDFs can't select text, so they only get Pages, Bookmarks and (page) Notes.
    val ids = if (isPdf) listOf(0, 1, 4) else listOf(0, 1, 2, 3, 4)
    fun titleOf(id: Int): String = when (id) {
        0 -> if (isPdf) "Pages" else "Contents"
        1 -> "Bookmarks (${bookmarks.size})"
        2 -> "Highlights (${highlights.size})"
        3 -> "Quotes (${quotes.size})"
        else -> "Notes (${notes.size})"
    }
    val selected = tab.coerceIn(0, ids.lastIndex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        contentColor = TextHigh,
    ) {
        ScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 12.dp,
            containerColor = Panel,
            contentColor = Accent,
        ) {
            ids.forEachIndexed { i, id ->
                Tab(selected = selected == i, onClick = { tab = i }, text = { Text(titleOf(id), maxLines = 1) })
            }
        }

        when (ids[selected]) {
            0 -> if (isPdf) {
                GoToPage(pageCount, currentPage, onGoToPage)
            } else {
                LazyColumn(Modifier.padding(bottom = 24.dp)) {
                    items(toc) { item ->
                        val current = item.spineIndex == currentChapter
                        Text(
                            item.title,
                            color = if (current) Accent else TextHigh,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenToc(item) }
                                .padding(start = (20 + item.depth.coerceAtMost(4) * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        )
                        HorizontalDivider(color = Hairline)
                    }
                }
            }
            1 -> MarkList(bookmarks, "No bookmarks yet. Tap the bookmark icon while reading.", false, onOpenMark, onDeleteMark)
            2 -> MarkList(highlights, "No highlights yet. Select text and choose Highlight.", true, onOpenMark, onDeleteMark)
            3 -> MarkList(quotes, "No quotes yet. Select text and choose Quote.", true, onOpenMark, onDeleteMark)
            else -> MarkList(
                notes,
                if (isPdf) "No notes yet. Tap the note icon in the top bar to add one to this page."
                else "No notes yet. Select text and choose Note.",
                true,
                onOpenMark,
                onDeleteMark,
            )
        }
    }
}


@Composable
private fun GoToPage(pageCount: Int, currentPage: Int, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("You're on page ${currentPage + 1} of $pageCount.", color = TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text("Go to page") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { text.toIntOrNull()?.let { onGo((it - 1).coerceIn(0, pageCount - 1)) } },
                enabled = text.toIntOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1300)),
            ) { Text("Go") }
        }
        Text(
            "PDF outlines (chapter lists) aren't available yet; use the slider or this box to jump.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MarkList(
    marks: List<BookmarkEntity>,
    empty: String,
    showQuote: Boolean,
    onOpen: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
) {
    if (marks.isEmpty()) {
        Text(empty, color = TextMuted, modifier = Modifier.padding(20.dp))
        return
    }
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        items(marks, key = { it.id }) { mark ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(mark) }
                    .padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (showQuote && mark.quote.isNotEmpty()) {
                        Text(mark.quote, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (mark.note.isNotEmpty()) {
                        Text(mark.note, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(mark.label, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(dates.format(Date(mark.createdAt)), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onDelete(mark) }) { Icon(Icons.Filled.Delete, "Delete", tint = TextMuted) }
            }
            HorizontalDivider(color = Hairline)
        }
    }
}