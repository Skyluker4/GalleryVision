// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned

class TestGestureActivity : ComponentActivity() {

    @Volatile
    var ready = false
        private set

    var zoomFactor = 1f
        private set
    var zoomCount = 0
        private set
    var seekDeltaMs = 0L
        private set
    var seekStarted = false
        private set
    var seekEnded = false
        private set
    var volume = 0f
        private set
    var brightness = 0f
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { ready = true }
                    .playerGestures(
                        onSeekStart = { seekStarted = true },
                        onSeekDelta = { seekDeltaMs += it },
                        onSeekEnd = { seekEnded = true },
                        onVolume = { volume += it },
                        onBrightness = { brightness += it },
                        onZoom = { zoomCount++; zoomFactor *= it },
                    ),
            )
        }
    }
}
