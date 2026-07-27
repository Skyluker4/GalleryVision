// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.model.NoteTargetKind
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel
    @Inject
    constructor(
        private val repository: LibraryRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

        private val _folder = MutableStateFlow<FolderEntity?>(null)
        val folder: StateFlow<FolderEntity?> = _folder

        val media: StateFlow<List<MediaEntity>> =
            repository
                .mediaForFolder(folderId)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val notes: StateFlow<List<NoteEntity>> =
            repository
                .notesFor(NoteTargetKind.FOLDER, folderId)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        init {
            viewModelScope.launch { _folder.value = repository.getFolder(folderId) }
        }

        fun addNote(
            body: String,
            parentNoteId: Long?,
        ) {
            val text = body.trim()
            if (text.isEmpty()) return
            viewModelScope.launch {
                repository.addNote(
                    NoteEntity(
                        targetKind = NoteTargetKind.FOLDER,
                        targetId = folderId,
                        body = text,
                        parentNoteId = parentNoteId,
                    ),
                )
            }
        }

        fun deleteNote(noteId: Long) {
            viewModelScope.launch { repository.deleteNote(noteId) }
        }
    }
