// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.DetectionSource
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import com.lukesimmons.galleryvision.inference.OcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val ocrEngine: OcrEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    private val _regions = MutableStateFlow<List<OcrEngine.TextRegion>>(emptyList())
    val regions: StateFlow<List<OcrEngine.TextRegion>> = _regions

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    private val _selected = MutableStateFlow<OcrEngine.TextRegion?>(null)
    val selected: StateFlow<OcrEngine.TextRegion?> = _selected

    fun load(mediaId: Long) {
        viewModelScope.launch {
            val m = repository.getMediaById(mediaId) ?: return@launch
            _media.value = m
            val cached = repository.detectionsFor(mediaId).first()
            if (cached.isNotEmpty()) {
                _regions.value = cached.map { it.toTextRegion() }
            } else {
                runOcr(m)
            }
        }
    }

    private suspend fun runOcr(m: MediaEntity) = withContext(Dispatchers.Default) {
        _processing.value = true
        try {
            val bitmap = loadBitmap(m.sourceUri) ?: return@withContext
            val results = ocrEngine.ocr(bitmap)
            _regions.value = results
            repository.saveDetections(results.map { it.toDetectionEntity(m.id) })
        } finally {
            _processing.value = false
        }
    }

    private fun loadBitmap(sourceUri: String): Bitmap? =
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(sourceUri))
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()

    fun select(region: OcrEngine.TextRegion?) {
        _selected.value = region
    }

    fun selectAt(normX: Float, normY: Float) {
        _selected.value = _regions.value.firstOrNull { pointInQuad(normX, normY, it.quad) }
    }

    fun allText(): String = _regions.value.joinToString("\n") { it.text }

    private fun pointInQuad(x: Float, y: Float, quad: FloatArray): Boolean {
        var inside = false
        var j = 3
        for (i in 0 until 4) {
            val xi = quad[i * 2]; val yi = quad[i * 2 + 1]
            val xj = quad[j * 2]; val yj = quad[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun DetectionEntity.toTextRegion(): OcrEngine.TextRegion {
        val quad = poly?.split(',')?.map { it.toFloat() }?.toFloatArray()
            ?: floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        return OcrEngine.TextRegion(valueText ?: "", quad, confidence)
    }

    private fun OcrEngine.TextRegion.toDetectionEntity(mediaId: Long): DetectionEntity {
        var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
        for (k in 0 until 4) {
            minX = minOf(minX, quad[k * 2]); maxX = maxOf(maxX, quad[k * 2])
            minY = minOf(minY, quad[k * 2 + 1]); maxY = maxOf(maxY, quad[k * 2 + 1])
        }
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.TEXT,
            source = DetectionSource.AUTO,
            left = minX, top = minY, right = maxX, bottom = maxY,
            poly = quad.joinToString(",") { it.toString() },
            label = null,
            valueText = text,
            confidence = confidence,
            clusterId = null,
            edited = false,
        )
    }
}
