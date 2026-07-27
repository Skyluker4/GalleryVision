// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.core.model.MediaType
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VideoUiState(
    val loading: Boolean = true,
    val media: MediaEntity? = null,
    val error: String? = null,
)

@HiltViewModel
class VideoViewModel
    @Inject
    constructor(
        private val app: Application,
        private val repository: LibraryRepository,
        private val settings: SettingsStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])

        private val _ui = MutableStateFlow(VideoUiState())
        val ui: StateFlow<VideoUiState> = _ui.asStateFlow()

        private val _controller = MutableStateFlow<MpvPlayerController?>(null)
        val controller: StateFlow<MpvPlayerController?> = _controller.asStateFlow()

        init {
            viewModelScope.launch {
                val media = repository.getMediaById(mediaId)
                if (media == null) {
                    _ui.value = VideoUiState(loading = false, error = "Media not found")
                    return@launch
                }
                val options = linkedMapOf<String, String>()
                if (media.type == MediaType.GIF || media.type == MediaType.ANIMATED) {
                    options["loop-file"] = "inf"
                    options["demuxer-lavf-o"] = "ignore_loop=0"
                }
                parseMpvConf(settings.mpvConfig.first()).forEach { (k, v) -> options[k] = v }
                val c = MpvPlayerController(app, options)
                _controller.value = c
                c.load(resolveSource(media))
                _ui.value = VideoUiState(loading = false, media = media)
            }
        }

        /** libmpv can't open content:// URIs; use the real path or hand it an owned fd. */
        private fun resolveSource(media: MediaEntity): String {
            val f = File(media.path)
            if (f.canRead()) return f.absolutePath
            val pfd =
                app.contentResolver.openFileDescriptor(Uri.parse(media.sourceUri), "r")
                    ?: return media.sourceUri
            return "fdclose://${pfd.detachFd()}"
        }

        override fun onCleared() {
            _controller.value?.release()
            _controller.value = null
        }

        companion object {
            fun parseMpvConf(text: String): List<Pair<String, String>> =
                text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val idx = line.indexOf('=')
                        if (idx <= 0) {
                            null
                        } else {
                            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                        }
                    }.toList()
        }
    }
