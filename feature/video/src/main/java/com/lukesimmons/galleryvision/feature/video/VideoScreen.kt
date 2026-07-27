// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun VideoScreen(
    mediaId: Long,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val player = controller?.state?.collectAsStateWithLifecycle()?.value ?: PlayerState()

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var surface by remember { mutableStateOf<Surface?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    // Fractional volume accumulator: per-event deltas are far below one stream
    // index step, so rounding each event would never move the volume.
    var volumeLevel by remember { mutableFloatStateOf(Float.NaN) }

    // Attach once both the (async-created) controller and the surface exist.
    LaunchedEffect(controller, surface) {
        val c = controller
        val s = surface
        if (c != null && s != null) c.attachSurface(s)
    }

    LaunchedEffect(controlsVisible, player.paused) {
        if (controlsVisible && !player.paused) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            surface = h.surface
                        }

                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
                            controller?.setSurfaceSize(w, ht)
                        }

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            surface = null
                            controller?.detachSurface()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(
                    onSeekStart = {
                        scrubbing = true
                        scrubMs = player.positionMs
                    },
                    onSeekDelta = { delta ->
                        val dur = player.durationMs
                        scrubMs = (scrubMs + delta).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                    },
                    onSeekEnd = {
                        controller?.seekTo(scrubMs)
                        scrubbing = false
                    },
                    onVolume = { frac ->
                        volumeLevel = adjustVolume(audioManager, frac, volumeLevel)
                    },
                    onBrightness = { frac -> adjustBrightness(context, frac) },
                    onZoom = { factor -> controller?.zoomBy(factor) },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { controller?.playPause() },
                    )
                },
        )

        if (scrubbing) {
            Text(
                text = "${formatTime(scrubMs)} / ${formatTime(player.durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val error = ui.error ?: player.error
        when {
            error != null -> Text(
                text = error,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            ui.loading || (!player.ready && !scrubbing) -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlsVisible && !scrubbing) {
            ControlsBar(
                positionMs = player.positionMs,
                durationMs = player.durationMs,
                paused = player.paused,
                onPlayPause = { controller?.playPause() },
                onSeekTo = { controller?.seekTo(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ControlsBar(
    positionMs: Long,
    durationMs: Long,
    paused: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderPos by remember(positionMs) { mutableFloatStateOf(positionMs.toFloat()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Slider(
            value = sliderPos.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { sliderPos = it },
            onValueChangeFinished = { onSeekTo(sliderPos.toLong()) },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPlayPause) {
                Text(if (paused) "Play" else "Pause", color = Color.White)
            }
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun adjustVolume(audioManager: AudioManager, fractionDelta: Float, level: Float): Float {
    val stream = AudioManager.STREAM_MUSIC
    val max = audioManager.getStreamMaxVolume(stream).toFloat()
    val base = if (level.isNaN()) audioManager.getStreamVolume(stream).toFloat() else level
    val next = (base + fractionDelta * max).coerceIn(0f, max)
    val rounded = next.toInt()
    if (rounded != audioManager.getStreamVolume(stream)) {
        audioManager.setStreamVolume(stream, rounded, 0)
    }
    return next
}

private fun adjustBrightness(context: Context, fractionDelta: Float) {
    val activity = context as? Activity ?: return
    val attrs = activity.window.attributes
    val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
    attrs.screenBrightness = (current + fractionDelta).coerceIn(0.01f, 1f)
    activity.window.attributes = attrs
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
