// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.FaceClusterInfo
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: LibraryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class ClusterUi(
        val info: FaceClusterInfo,
        val face: Bitmap?,
    )

    private val _clusters = MutableStateFlow<List<ClusterUi>>(emptyList())
    val clusters: StateFlow<List<ClusterUi>> = _clusters

    private val _reclustering = MutableStateFlow(false)
    val reclustering: StateFlow<Boolean> = _reclustering

    private val deniedFaces: StateFlow<Set<String>> =
        repository.denyList(DenyKind.FACE)
            .map { list -> list.map { it.value.lowercase() }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            combine(repository.clustersWithRepresentative(), deniedFaces) { infos, denied ->
                infos.filter { info -> info.name?.lowercase() !in denied }
            }.collect { infos ->
                _clusters.value = infos.map { info -> ClusterUi(info, loadFaceCrop(info)) }
            }
        }
    }

    fun denyCluster(name: String) {
        viewModelScope.launch { repository.addDeny(DenyEntity(DenyKind.FACE, name)) }
    }

    fun recluster() {
        viewModelScope.launch {
            _reclustering.value = true
            try {
                repository.reclusterFaces()
                refresh()
            } finally {
                _reclustering.value = false
            }
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameCluster(id, name) }
    }

    fun linkContact(id: Long, lookupKey: String?) {
        viewModelScope.launch { repository.linkClusterToContact(id, lookupKey) }
    }

    private suspend fun loadFaceCrop(info: FaceClusterInfo): Bitmap? {
        val mediaId = info.representativeMediaId ?: return null
        val box = info.faceBox ?: return null
        val media = repository.getMediaById(mediaId) ?: return null
        val bitmap = loadBitmap(media.sourceUri) ?: return null
        return cropBox(bitmap, box)
    }

    private fun loadBitmap(sourceUri: String): Bitmap? =
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(sourceUri))
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()

    private fun cropBox(src: Bitmap, box: FloatArray): Bitmap? {
        val l = (box[0] * src.width).toInt().coerceIn(0, src.width - 1)
        val t = (box[1] * src.height).toInt().coerceIn(0, src.height - 1)
        val r = (box[2] * src.width).toInt().coerceIn(l + 1, src.width)
        val b = (box[3] * src.height).toInt().coerceIn(t + 1, src.height)
        return runCatching { Bitmap.createBitmap(src, l, t, r - l, b - t) }.getOrNull()
    }
}
