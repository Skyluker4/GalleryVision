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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lukesimmons.galleryvision.core.model.DetectionKind

/** Full-image viewer: positioned OCR overlays, tap-to-select, copy, manual edit, dictionary, deny. */
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
    var showEdit by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

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
                            viewModel.selectAt(
                                (tap.x - offX) / (imgW * scale),
                                (tap.y - offY) / (imgH * scale),
                            )
                        }
                    },
            ) {
                regions.forEach { region ->
                    val q = region.quad()
                    val path = Path().apply {
                        moveTo(offX + q[0] * imgW * scale, offY + q[1] * imgH * scale)
                        for (k in 1 until 4) {
                            lineTo(offX + q[k * 2] * imgW * scale, offY + q[k * 2 + 1] * imgH * scale)
                        }
                        close()
                    }
                    val isSel = region.id == selected?.id
                    drawPath(path, color = if (isSel) Color(0x5500FF88) else kindFill(region.kind))
                    drawPath(
                        path,
                        color = if (isSel) Color(0xFF00FF88) else kindStroke(region.kind),
                        style = Stroke(width = if (isSel) 4f else 2f),
                    )
                }
            }

            if (processing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null) {
                val sel = selected!!
                Text(
                    text = sel.valueText ?: "",
                    modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(sel.valueText ?: "")) }) { Text("Copy") }
                TextButton(onClick = { editText = sel.valueText ?: ""; showEdit = true }) { Text("Edit") }
                TextButton(onClick = { viewModel.addWordToDictionary(sel.valueText ?: "") }) { Text("+ Dict") }
                TextButton(onClick = { viewModel.addWordToDenyList(sel.valueText ?: "") }) { Text("Deny") }
                TextButton(onClick = { viewModel.select(null) }) { Text("X") }
            } else {
                Text(
                    text = if (processing) "Running OCR…" else "${regions.size} text regions",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (regions.isNotEmpty()) {
                    Button(onClick = { clipboard.setText(AnnotatedString(viewModel.allText())) }) {
                        Text("Copy all")
                    }
                }
            }
        }
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Edit text") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.editSelected(editText); showEdit = false }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("Cancel") }
            },
        )
    }
}

private fun kindFill(kind: DetectionKind): Color = when (kind) {
    DetectionKind.TEXT -> Color(0x2200CCFF)
    DetectionKind.FACE -> Color(0x2266BB6A)
    DetectionKind.OBJECT -> Color(0x22FF9800)
}

private fun kindStroke(kind: DetectionKind): Color = when (kind) {
    DetectionKind.TEXT -> Color(0xFF00CCFF)
    DetectionKind.FACE -> Color(0xFF66BB6A)
    DetectionKind.OBJECT -> Color(0xFFFF9800)
}
