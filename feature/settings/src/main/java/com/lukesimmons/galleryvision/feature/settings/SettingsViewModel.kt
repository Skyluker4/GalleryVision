// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: LibraryRepository,
        private val settings: SettingsStore,
    ) : ViewModel() {
        val mpvConfig: StateFlow<String> =
            settings.mpvConfig.stateIn(viewModelScope, SharingStarted.Eagerly, "")

        val dictionary: StateFlow<List<String>> =
            settings.dictionaryWords
                .map { it.sorted() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val denyWords: StateFlow<List<String>> = denyValues(DenyKind.WORD)
        val denyObjects: StateFlow<List<String>> = denyValues(DenyKind.OBJECT)
        val denyTags: StateFlow<List<String>> = denyValues(DenyKind.TAG)
        val denyFaces: StateFlow<List<String>> = denyValues(DenyKind.FACE)

        private fun denyValues(kind: DenyKind): StateFlow<List<String>> =
            repository
                .denyList(kind)
                .map { list -> list.map { it.value }.sorted() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        fun saveMpvConfig(text: String) {
            viewModelScope.launch { settings.setMpvConfig(text) }
        }

        fun addDictionaryWord(word: String) {
            viewModelScope.launch { settings.addDictionaryWord(word) }
        }

        fun removeDictionaryWord(word: String) {
            viewModelScope.launch { settings.removeDictionaryWord(word) }
        }

        fun addDeny(
            kind: DenyKind,
            value: String,
        ) {
            val v = value.trim()
            if (v.isEmpty()) return
            viewModelScope.launch { repository.addDeny(DenyEntity(kind, v)) }
        }

        fun removeDeny(
            kind: DenyKind,
            value: String,
        ) {
            viewModelScope.launch { repository.removeDeny(kind, value) }
        }
    }
