package com.focuslife.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.store by preferencesDataStore("focuslife_preferences")
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val light = booleanPreferencesKey("light_theme")
    val lightTheme = context.store.data.map { it[light] ?: false }
    suspend fun setLight(value: Boolean) { context.store.edit { it[light] = value } }
    suspend fun clear() { context.store.edit { it.clear() } }
}
