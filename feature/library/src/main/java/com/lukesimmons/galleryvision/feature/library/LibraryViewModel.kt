// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.model.SortSpec
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Non-null while a search is active; holds the matching media (empty list = no matches). */
    private val _searchResults = MutableStateFlow<List<MediaEntity>?>(null)
    val searchResults: StateFlow<List<MediaEntity>?> = _searchResults

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _sort = MutableStateFlow<SortSpec?>(null)
    val sort: StateFlow<SortSpec?> = _sort

    fun setQuery(value: String) {
        _query.value = value
        if (value.isBlank()) clearSearch()
    }

    fun setSort(value: SortSpec?) {
        _sort.value = value
        if (_query.value.isNotBlank()) search()
    }

    fun search() {
        val q = _query.value
        if (q.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _searching.value = true
            try {
                _searchResults.value = repository.search(q, _sort.value)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _searchResults.value = null
    }
}
