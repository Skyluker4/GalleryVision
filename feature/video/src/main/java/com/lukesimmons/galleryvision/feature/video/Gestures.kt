// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private enum class DragMode { SEEK, VOLUME, BRIGHTNESS, ZOOM }

/**
 * Player gestures (R7): horizontal drag = scrub, vertical drag on the left half =
 * brightness, vertical drag on the right half = volume, two-finger pinch = zoom.
 * Axis locks on the first movement past touch slop. Tap/double-tap are handled
 * separately by the caller via detectTapGestures.
 */
fun Modifier.playerGestures(
    onSeekStart: () -> Unit,
    onSeekDelta: (deltaMs: Long) -> Unit,
    onSeekEnd: () -> Unit,
    onVolume: (fractionDelta: Float) -> Unit,
    onBrightness: (fractionDelta: Float) -> Unit,
    onZoom: (factor: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val slop = viewConfiguration.touchSlop
        var mode: DragMode? = null
        var prevSpan = -1f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                if (mode == DragMode.SEEK) onSeekEnd()
                break
            }

            if (pressed.size >= 2) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                if (prevSpan > 0f && span > 0f) {
                    val factor = span / prevSpan
                    if (factor != 1f) onZoom(factor)
                }
                prevSpan = span
                pressed.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                mode = DragMode.ZOOM
                continue
            }

            val change = pressed[0]
            if (mode == null) {
                val total = change.position - start
                if (total.getDistance() < slop) continue
                mode = when {
                    abs(total.x) >= abs(total.y) -> DragMode.SEEK
                    start.x < size.width / 2f -> DragMode.BRIGHTNESS
                    else -> DragMode.VOLUME
                }
                if (mode == DragMode.SEEK) onSeekStart()
            }

            val delta = change.positionChange()
            when (mode) {
                DragMode.SEEK -> {
                    val deltaMs = (delta.x / size.width * SEEK_FULL_WIDTH_MS).toLong()
                    if (deltaMs != 0L) onSeekDelta(deltaMs)
                }
                DragMode.VOLUME -> onVolume(-delta.y / size.height)
                DragMode.BRIGHTNESS -> onBrightness(-delta.y / size.height)
                else -> Unit
            }
            if (delta != Offset.Zero) change.consume()
        }
    }
}

private const val SEEK_FULL_WIDTH_MS = 90_000
