// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.ui.NotesSheet

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
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showEdit by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    // Normalized [left, top, right, bottom] of the selected box while it is dragged.
    var editBox by remember(selected?.id) {
        mutableStateOf(selected?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) })
    }

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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(regions, selected?.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val sel = selected
                                val eb = editBox
                                var mode = 0 // 0 = tap-to-select, 1 = move box, 2 = resize corner
                                var cornerIdx = -1
                                if (sel != null && eb != null) {
                                    val hitR = 48f
                                    val corners =
                                        listOf(
                                            Offset(offX + eb[0] * imgW * scale, offY + eb[1] * imgH * scale),
                                            Offset(offX + eb[2] * imgW * scale, offY + eb[1] * imgH * scale),
                                            Offset(offX + eb[2] * imgW * scale, offY + eb[3] * imgH * scale),
                                            Offset(offX + eb[0] * imgW * scale, offY + eb[3] * imgH * scale),
                                        )
                                    cornerIdx = corners.indexOfFirst { (it - down.position).getDistance() < hitR }
                                    if (cornerIdx >= 0) {
                                        mode = 2
                                    } else {
                                        val nx = (down.position.x - offX) / (imgW * scale)
                                        val ny = (down.position.y - offY) / (imgH * scale)
                                        if (nx in eb[0]..eb[2] && ny in eb[1]..eb[3]) mode = 1
                                    }
                                }

                                if (mode == 0) {
                                    val start = down.position
                                    var moved = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                            moved = true
                                            break
                                        }
                                    }
                                    if (!moved) {
                                        viewModel.selectAt(
                                            (start.x - offX) / (imgW * scale),
                                            (start.y - offY) / (imgH * scale),
                                        )
                                    }
                                } else {
                                    var lastX = down.position.x
                                    var lastY = down.position.y
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val ndx = (change.position.x - lastX) / (imgW * scale)
                                        val ndy = (change.position.y - lastY) / (imgH * scale)
                                        lastX = change.position.x
                                        lastY = change.position.y
                                        val b = editBox!!.copyOf()
                                        if (mode == 1) {
                                            val w = b[2] - b[0]
                                            val h = b[3] - b[1]
                                            b[0] = (b[0] + ndx).coerceIn(0f, 1f - w)
                                            b[2] = b[0] + w
                                            b[1] = (b[1] + ndy).coerceIn(0f, 1f - h)
                                            b[3] = b[1] + h
                                        } else {
                                            when (cornerIdx) {
                                                0 -> {
                                                    b[0] = (b[0] + ndx).coerceAtMost(b[2] - 0.01f)
                                                    b[1] = (b[1] + ndy).coerceAtMost(b[3] - 0.01f)
                                                }
                                                1 -> {
                                                    b[2] = (b[2] + ndx).coerceAtLeast(b[0] + 0.01f)
                                                    b[1] = (b[1] + ndy).coerceAtMost(b[3] - 0.01f)
                                                }
                                                2 -> {
                                                    b[2] = (b[2] + ndx).coerceAtLeast(b[0] + 0.01f)
                                                    b[3] = (b[3] + ndy).coerceAtLeast(b[1] + 0.01f)
                                                }
                                                3 -> {
                                                    b[0] = (b[0] + ndx).coerceAtMost(b[2] - 0.01f)
                                                    b[3] = (b[3] + ndy).coerceAtLeast(b[1] + 0.01f)
                                                }
                                            }
                                            b[0] = b[0].coerceIn(0f, 1f)
                                            b[1] = b[1].coerceIn(0f, 1f)
                                            b[2] = b[2].coerceIn(0f, 1f)
                                            b[3] = b[3].coerceIn(0f, 1f)
                                        }
                                        editBox = b
                                        change.consume()
                                    }
                                    val b = editBox!!
                                    viewModel.updateRegionPosition(sel ?: return@awaitEachGesture, b[0], b[1], b[2], b[3])
                                }
                            }
                        },
            ) {
                regions.forEach { region ->
                    val q = region.quad()
                    val path =
                        Path().apply {
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

                val eb = editBox
                if (selected != null && eb != null) {
                    val corners =
                        listOf(
                            Offset(offX + eb[0] * imgW * scale, offY + eb[1] * imgH * scale),
                            Offset(offX + eb[2] * imgW * scale, offY + eb[1] * imgH * scale),
                            Offset(offX + eb[2] * imgW * scale, offY + eb[3] * imgH * scale),
                            Offset(offX + eb[0] * imgW * scale, offY + eb[3] * imgH * scale),
                        )
                    corners.forEach { c ->
                        drawCircle(Color(0xFF00FF88), radius = 12f, center = c)
                        drawCircle(Color.White, radius = 6f, center = c)
                    }
                }
            }

            if (processing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            modifier =
                Modifier
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
                TextButton(onClick = {
                    editText = sel.valueText ?: ""
                    showEdit = true
                }) { Text("Edit") }
                TextButton(onClick = { viewModel.addWordToDictionary(sel.valueText ?: "") }) { Text("+ Dict") }
                TextButton(onClick = { viewModel.addWordToDenyList(sel.valueText ?: "") }) { Text("Deny") }
                TextButton(onClick = { viewModel.select(null) }) { Text("X") }
            } else {
                Text(
                    text = if (processing) "Running OCR…" else "${regions.size} text regions",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { showNotes = true }) {
                    Text(if (notes.isEmpty()) "Notes" else "Notes (${notes.size})")
                }
                TextButton(onClick = { showTags = true }) {
                    Text(if (tags.isEmpty()) "Tags" else "Tags (${tags.size})")
                }
                if (regions.isNotEmpty()) {
                    Button(onClick = { clipboard.setText(AnnotatedString(viewModel.allText())) }) {
                        Text("Copy all")
                    }
                }
            }
        }
    }

    if (showNotes) {
        NotesSheet(
            notes = notes,
            onAdd = viewModel::addNote,
            onDelete = viewModel::deleteNote,
            onDismiss = { showNotes = false },
        )
    }

    if (showTags) {
        TagsSheet(
            tags = tags,
            onAdd = viewModel::addTag,
            onRemove = viewModel::removeTag,
            onDismiss = { showTags = false },
        )
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
                TextButton(onClick = {
                    viewModel.editSelected(editText)
                    showEdit = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("Cancel") }
            },
        )
    }
}

private fun kindFill(kind: DetectionKind): Color =
    when (kind) {
        DetectionKind.TEXT -> Color(0x2200CCFF)
        DetectionKind.FACE -> Color(0x2266BB6A)
        DetectionKind.OBJECT -> Color(0x22FF9800)
    }

private fun kindStroke(kind: DetectionKind): Color =
    when (kind) {
        DetectionKind.TEXT -> Color(0xFF00CCFF)
        DetectionKind.FACE -> Color(0xFF66BB6A)
        DetectionKind.OBJECT -> Color(0xFFFF9800)
    }
