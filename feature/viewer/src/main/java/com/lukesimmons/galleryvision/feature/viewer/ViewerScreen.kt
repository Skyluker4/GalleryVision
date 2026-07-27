// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

/** Full-image viewer with positioned OCR text overlays, tap-to-select, and copy. */
@Composable
fun ViewerScreen(
    mediaId: Long,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(mediaId) { viewModel.load(mediaId) }

    val media by viewModel.media.collectAsStateWithLifecycle()
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val processing by viewModel.processing.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val viewW = constraints.maxWidth.toFloat()
            val viewH = constraints.maxHeight.toFloat()
            val imgW = (media?.width ?: 0).toFloat().takeIf { it > 0 } ?: viewW
            val imgH = (media?.height ?: 0).toFloat().takeIf { it > 0 } ?: viewH
            val scale = minOf(viewW / imgW, viewH / imgH)
            val offX = (viewW - imgW * scale) / 2f
            val offY = (viewH - imgH * scale) / 2f

            AsyncImage(
                model = media?.sourceUri,
                contentDescription = media?.path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(regions) {
                        detectTapGestures { tap ->
                            val nx = (tap.x - offX) / (imgW * scale)
                            val ny = (tap.y - offY) / (imgH * scale)
                            viewModel.selectAt(nx, ny)
                        }
                    },
            ) {
                regions.forEach { region ->
                    val path = Path().apply {
                        moveTo(offX + region.quad[0] * imgW * scale, offY + region.quad[1] * imgH * scale)
                        for (k in 1 until 4) {
                            lineTo(offX + region.quad[k * 2] * imgW * scale, offY + region.quad[k * 2 + 1] * imgH * scale)
                        }
                        close()
                    }
                    val isSel = region === selected
                    drawPath(path, color = if (isSel) Color(0x5500FF88) else Color(0x2200CCFF))
                    drawPath(
                        path,
                        color = if (isSel) Color(0xFF00FF88) else Color(0xFF00CCFF),
                        style = Stroke(width = if (isSel) 4f else 2f),
                    )
                }
            }

            if (processing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    processing -> "Running OCR…"
                    selected != null -> selected!!.text
                    else -> "${regions.size} text regions"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
            if (selected != null) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(selected!!.text)) }) {
                    Text("Copy")
                }
            }
            if (regions.isNotEmpty()) {
                Button(onClick = { clipboard.setText(AnnotatedString(viewModel.allText())) }) {
                    Text("Copy all")
                }
            }
        }
    }
}
