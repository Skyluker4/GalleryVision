// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lukesimmons.galleryvision.ui.theme.GalleryVisionTheme

/**
 * Placeholder entry point. The full gallery UI (grid, viewer, search, video) is built
 * across the M1-M6 milestones on the multi-module architecture; see docs/DESIGN.md.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GalleryVisionTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Placeholder()
                }
            }
        }
    }
}

@Composable
fun Placeholder(modifier: Modifier = Modifier) {
    Text(text = "GalleryVision", modifier = modifier)
}
