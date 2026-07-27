// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.dao.FolderWithCount
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel
    @Inject
    constructor(
        private val repository: LibraryRepository,
        private val settings: SettingsStore,
    ) : ViewModel() {
        val folders: StateFlow<List<FolderWithCount>> =
            repository
                .foldersWithCounts()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val policies: StateFlow<Map<Long, FolderPolicyMode>> =
            repository
                .folderPolicies()
                .map { list -> list.associate { it.folderId to it.mode } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        val allowListOnly: StateFlow<Boolean> =
            settings.allowListOnly.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        fun setPolicy(
            folderId: Long,
            mode: FolderPolicyMode?,
        ) {
            viewModelScope.launch { repository.setFolderPolicy(folderId, mode) }
        }

        fun setAllowListOnly(value: Boolean) {
            viewModelScope.launch { settings.setAllowListOnly(value) }
        }
    }
