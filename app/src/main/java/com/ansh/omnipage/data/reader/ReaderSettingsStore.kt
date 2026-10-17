package com.ansh.omnipage.data.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "omnipage_reader")

class ReaderSettingsStore(private val context: Context) {

    private val theme = stringPreferencesKey("theme")
    private val font = stringPreferencesKey("font")
    private val fontSize = intPreferencesKey("font_size")
    private val lineHeight = floatPreferencesKey("line_height")
    private val margin = intPreferencesKey("margin")
    private val justify = booleanPreferencesKey("justify")
    private val paged = booleanPreferencesKey("paged")
    private val brightness = floatPreferencesKey("brightness")
    private val keepOn = booleanPreferencesKey("keep_screen_on")
    private val tapZones = booleanPreferencesKey("tap_zones")
    private val pdfPaged = booleanPreferencesKey("pdf_paged")
    private val pdfInvert = booleanPreferencesKey("pdf_invert")

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it) }
        .distinctUntilChanged()

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val next = transform(decode(prefs))
            prefs[theme] = next.theme.name
            prefs[font] = next.font.name
            prefs[fontSize] = next.fontSize
            prefs[lineHeight] = next.lineHeight
            prefs[margin] = next.margin
            prefs[justify] = next.justify
            prefs[paged] = next.paged
            prefs[brightness] = next.brightness
            prefs[keepOn] = next.keepScreenOn
            prefs[tapZones] = next.tapZones
            prefs[pdfPaged] = next.pdfPaged
            prefs[pdfInvert] = next.pdfInvert
        }
    }

    private fun decode(p: Preferences): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = ReaderTheme.entries.firstOrNull { it.name == p[theme] } ?: d.theme,
            font = ReaderFont.entries.firstOrNull { it.name == p[font] } ?: d.font,
            fontSize = (p[fontSize] ?: d.fontSize).coerceIn(10, 40),
            lineHeight = (p[lineHeight] ?: d.lineHeight).coerceIn(1.0f, 2.6f),
            margin = (p[margin] ?: d.margin).coerceIn(0, 64),
            justify = p[justify] ?: d.justify,
            paged = p[paged] ?: d.paged,
            brightness = p[brightness] ?: d.brightness,
            keepScreenOn = p[keepOn] ?: d.keepScreenOn,
            tapZones = p[tapZones] ?: d.tapZones,
            pdfPaged = p[pdfPaged] ?: d.pdfPaged,
            pdfInvert = p[pdfInvert] ?: d.pdfInvert,
        )
    }
}
