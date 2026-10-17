package com.ansh.omnipage.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ansh.omnipage.data.SortOrder
import com.ansh.omnipage.data.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "omnipage_settings")

class SettingsStore(private val context: Context) {
    private val viewKey = stringPreferencesKey("view_mode")
    private val sortKey = stringPreferencesKey("sort_order")

    private val prefs: Flow<Preferences> = context.settingsDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val viewMode: Flow<ViewMode> = prefs.map { ViewMode.from(it[viewKey]) }.distinctUntilChanged()
    val sortOrder: Flow<SortOrder> = prefs.map { SortOrder.from(it[sortKey]) }.distinctUntilChanged()

    suspend fun setViewMode(mode: ViewMode) {
        context.settingsDataStore.edit { it[viewKey] = mode.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.settingsDataStore.edit { it[sortKey] = order.name }
    }
}
