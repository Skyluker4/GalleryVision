// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlin.math.ln
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerState(
    val ready: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val paused: Boolean = true,
    val zoom: Double = 0.0,
)

/**
 * Wraps the libmpv JNI bridge using mpv-android's proven lifecycle:
 * create -> options -> init -> (on surface) attach + force-window -> loadfile;
 * (on surface loss) vo=null -> detachSurface.
 */
class MpvPlayerController(
    context: Context,
    options: Map<String, String> = emptyMap(),
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var mpv: MPVLib? = null
    private var pendingSource: String? = null
    private var attached = false

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: String) = Unit

        override fun eventProperty(property: String, value: Boolean) {
            if (property == "pause") _state.update { it.copy(paused = value) }
        }

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> _state.update { it.copy(positionMs = (value * 1000).toLong()) }
                "duration" -> _state.update { it.copy(durationMs = (value * 1000).toLong()) }
                "video-zoom" -> _state.update { it.copy(zoom = value) }
            }
        }

        override fun event(eventId: Int) {
            if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) {
                _state.update { it.copy(ready = true) }
            }
        }
    }

    init {
        try {
            val instance = MPVLib.create(context)
            if (instance == null) {
                _state.update { it.copy(error = "libmpv failed to initialize") }
            } else {
                mpv = instance
                instance.setOptionString("config", "no")
                instance.setOptionString("vo", "gpu")
                instance.setOptionString("hwdec", "auto-safe")
                options.forEach { (k, v) -> instance.setOptionString(k, v) }
                instance.init()
                instance.setOptionString("force-window", "no")
                instance.setOptionString("idle", "once")
                instance.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                instance.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                instance.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                instance.observeProperty("video-zoom", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                instance.addObserver(observer)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "libmpv unavailable", t)
            _state.update { it.copy(error = "Video engine unavailable: ${t.message}") }
        }
    }

    fun attachSurface(surface: Surface) {
        val m = mpv ?: return
        m.attachSurface(surface)
        m.setOptionString("force-window", "yes")
        attached = true
        val src = pendingSource
        if (src != null) {
            m.command(arrayOf("loadfile", src))
            pendingSource = null
        } else {
            m.setPropertyString("vo", "gpu")
        }
    }

    fun detachSurface() {
        val m = mpv ?: return
        m.setPropertyString("vo", "null")
        m.setPropertyString("force-window", "no")
        m.detachSurface()
        attached = false
    }

    fun setSurfaceSize(width: Int, height: Int) {
        mpv?.setPropertyString("android-surface-size", "${width}x$height")
    }

    fun load(source: String) {
        if (attached) {
            mpv?.command(arrayOf("loadfile", source))
        } else {
            pendingSource = source
        }
    }

    fun playPause() {
        mpv?.command(arrayOf("cycle", "pause"))
    }

    fun seekTo(positionMs: Long) {
        mpv?.command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute", "exact"))
    }

    fun seekBy(deltaMs: Long) {
        mpv?.command(arrayOf("seek", (deltaMs / 1000.0).toString(), "relative"))
    }

    /** mpv's video-zoom is log2-scaled (0 = fit, 1 = 2x); factor is a pinch ratio. */
    fun zoomBy(factor: Float) {
        val m = mpv ?: return
        val current = m.getPropertyDouble("video-zoom") ?: 0.0
        val next = (current + ln(factor.toDouble()) / ln(2.0)).coerceIn(0.0, 3.0)
        m.setPropertyDouble("video-zoom", next)
    }

    fun release() {
        try {
            mpv?.removeObserver(observer)
            if (attached) detachSurface()
            mpv?.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed", t)
        }
        mpv = null
    }

    companion object {
        private const val TAG = "MpvPlayerController"
    }
}
