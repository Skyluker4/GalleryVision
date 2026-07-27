// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    val media: Flow<PagingData<MediaEntity>> =
        repository.mediaPager().flow.cachedIn(viewModelScope)

    val mediaCount: StateFlow<Int> =
        repository.observeMediaCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun rescan() {
        viewModelScope.launch { repository.rescanNow() }
    }
}
