// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.DetectionSource
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import com.lukesimmons.galleryvision.inference.FaceEngine
import com.lukesimmons.galleryvision.inference.ObjectEngine
import com.lukesimmons.galleryvision.inference.OcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val ocrEngine: OcrEngine,
    private val faceEngine: FaceEngine,
    private val objectEngine: ObjectEngine,
    private val settings: SettingsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val mediaId = MutableStateFlow(0L)

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    private val _selected = MutableStateFlow<DetectionEntity?>(null)
    val selected: StateFlow<DetectionEntity?> = _selected

    private val deniedWords: StateFlow<Set<String>> =
        repository.denyList(DenyKind.WORD)
            .map { list -> list.map { it.value.lowercase() }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val dictionary: StateFlow<Set<String>> =
        settings.dictionaryWords.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val rawDetections = mediaId.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else repository.detectionsFor(id)
    }

    val regions: StateFlow<List<DetectionEntity>> =
        combine(rawDetections, deniedWords, dictionary) { dets, denied, dict ->
            dets.filter { det ->
                val text = det.valueText?.lowercase() ?: return@filter true
                denied.none { it.isNotEmpty() && text.contains(it) }
            }.map { det ->
                val corrected = snapToDictionary(det.valueText ?: "", dict)
                if (corrected == det.valueText) det else det.copy(valueText = corrected)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(id: Long) {
        mediaId.value = id
        viewModelScope.launch {
            val m = repository.getMediaById(id) ?: return@launch
            _media.value = m
            if (repository.detectionsFor(id).first().isEmpty()) {
                runDetection(m)
            }
        }
    }

    private suspend fun runDetection(m: MediaEntity) = withContext(Dispatchers.Default) {
        _processing.value = true
        try {
            val bitmap = loadBitmap(m.sourceUri) ?: return@withContext
            val ocrResults = ocrEngine.ocr(bitmap)
            val faceResults = runCatching { faceEngine.detect(bitmap) }.getOrDefault(emptyList())
            val objectResults = runCatching { objectEngine.detect(bitmap) }.getOrDefault(emptyList())
            repository.saveDetections(
                ocrResults.map { it.toDetectionEntity(m.id) } +
                    faceResults.map { it.toDetectionEntity(m.id) } +
                    objectResults.map { it.toDetectionEntity(m.id) },
            )
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

    fun select(region: DetectionEntity?) {
        _selected.value = region
    }

    fun selectAt(normX: Float, normY: Float) {
        _selected.value = regions.value.firstOrNull { pointInQuad(normX, normY, it.quad()) }
    }

    fun editSelected(newText: String) {
        val current = _selected.value ?: return
        val updated = current.copy(
            valueText = newText,
            source = DetectionSource.MANUAL,
            edited = true,
        )
        viewModelScope.launch {
            repository.updateDetection(updated)
            _selected.value = null
        }
    }

    fun addWordToDictionary(word: String) {
        viewModelScope.launch { settings.addDictionaryWord(word) }
    }

    fun addWordToDenyList(word: String) {
        viewModelScope.launch { repository.addDeny(DenyEntity(DenyKind.WORD, word)) }
    }

    fun allText(): String = regions.value.joinToString("\n") { it.valueText ?: "" }

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

    private fun snapToDictionary(text: String, dict: Set<String>): String {
        if (dict.isEmpty() || text.isBlank()) return text
        return text.split(' ').joinToString(" ") { token ->
            var best = token
            var bestDist = Int.MAX_VALUE
            val threshold = if (token.length <= 4) 1 else 2
            for (word in dict) {
                val d = levenshtein(token.lowercase(), word.lowercase())
                if (d < bestDist) { bestDist = d; best = word }
            }
            if (bestDist in 1..threshold) best else token
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = min(dp[j] + 1, min(dp[j - 1] + 1, prev + (if (a[i - 1] == b[j - 1]) 0 else 1)))
                prev = temp
            }
        }
        return dp[b.length]
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

    private fun FaceEngine.Face.toDetectionEntity(mediaId: Long): DetectionEntity {
        val box = floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.FACE,
            source = DetectionSource.AUTO,
            left = left, top = top, right = right, bottom = bottom,
            poly = box.joinToString(",") { it.toString() },
            label = null,
            valueText = null,
            confidence = score,
            clusterId = null,
            edited = false,
        )
    }

    private fun ObjectEngine.Detected.toDetectionEntity(mediaId: Long): DetectionEntity {
        val box = floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.OBJECT,
            source = DetectionSource.AUTO,
            left = left, top = top, right = right, bottom = bottom,
            poly = box.joinToString(",") { it.toString() },
            label = label,
            valueText = null,
            confidence = score,
            clusterId = null,
            edited = false,
        )
    }
}

fun DetectionEntity.quad(): FloatArray =
    poly?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 8 }?.toFloatArray()
        ?: floatArrayOf(left, top, right, top, right, bottom, left, bottom)
