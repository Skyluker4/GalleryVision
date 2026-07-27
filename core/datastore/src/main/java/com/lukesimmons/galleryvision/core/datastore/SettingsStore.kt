// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User-tunable settings and the custom OCR dictionary, persisted in DataStore. */
class SettingsStore(
    private val context: Context,
) {
    private object Keys {
        val DICTIONARY = stringSetPreferencesKey("custom_dictionary")
        val ALLOW_LIST_ONLY = booleanPreferencesKey("allow_list_only")
        val MPV_CONFIG = stringPreferencesKey("mpv_config")
    }

    val dictionaryWords: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.DICTIONARY] ?: emptySet() }

    suspend fun addDictionaryWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        context.dataStore.edit { it[Keys.DICTIONARY] = (it[Keys.DICTIONARY] ?: emptySet()) + w }
    }

    suspend fun removeDictionaryWord(word: String) {
        context.dataStore.edit { it[Keys.DICTIONARY] = (it[Keys.DICTIONARY] ?: emptySet()) - word }
    }

    val allowListOnly: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ALLOW_LIST_ONLY] ?: false }

    suspend fun setAllowListOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_LIST_ONLY] = value }
    }

    val mpvConfig: Flow<String> =
        context.dataStore.data.map { it[Keys.MPV_CONFIG] ?: "" }

    suspend fun setMpvConfig(value: String) {
        context.dataStore.edit { it[Keys.MPV_CONFIG] = value }
    }
}
