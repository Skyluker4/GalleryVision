// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlazeFaceDecoderTest {

    private fun blankOutputs(): Pair<Array<FloatArray>, FloatArray> {
        val reg = Array(2304) { FloatArray(16) }
        val cls = FloatArray(2304) { -10f }
        return reg to cls
    }

    @Test
    fun silenceProducesNoDetections() {
        val (reg, cls) = blankOutputs()
        assertEquals(emptyList(), BlazeFaceDecoder.decode(reg, cls))
    }

    @Test
    fun singleStrongAnchorProducesOneClampedBox() {
        val (reg, cls) = blankOutputs()
        // Anchor index 0 = grid cell (0,0), first anchor of that cell.
        cls[0] = 20f // sigmoid(20) ~ 1.0
        reg[0][0] = 0f
        reg[0][1] = 0f
        reg[0][2] = 0f
        reg[0][3] = 0f
        val out = BlazeFaceDecoder.decode(reg, cls)
        assertEquals(1, out.size)
        val box = out[0].box
        assertTrue(box[2] > box[0] && box[3] > box[1])
        assertTrue(box.all { it in 0f..1f })
        assertEquals(12, out[0].landmarks.size)
    }

    @Test
    fun subThresholdScoresAreDropped() {
        val (reg, cls) = blankOutputs()
        cls[0] = 0.1f // sigmoid(0.1) ~ 0.52 < 0.6 threshold
        val out = BlazeFaceDecoder.decode(reg, cls)
        assertEquals(emptyList(), out)
    }

    @Test
    fun partialBoxInsideLargerBoxIsSuppressed() {
        val (reg, cls) = blankOutputs()
        // Two overlapping detections: a big box and a small box inside it.
        cls[10] = 20f
        cls[11] = 19f
        // Big: center 0.5,0.5 size ~1.9 (exp(0.65)~1.9 at anchor scale 0.45)
        reg[10][0] = 120f
        reg[10][1] = 120f
        reg[10][2] = 120f
        reg[10][3] = 120f
        // Small: center near big's center, tiny size
        reg[11][0] = 121f
        reg[11][1] = 121f
        reg[11][2] = -200f
        reg[11][3] = -200f
        val out = BlazeFaceDecoder.decode(reg, cls)
        assertEquals(1, out.size)
        assertEquals(1f, out[0].score, 0.001f)
    }

    @Test
    fun distantBoxesBothSurvive() {
        val (reg, cls) = blankOutputs()
        cls[0] = 20f
        cls[2303] = 19f
        reg[0][0] = 0f
        reg[0][1] = 0f
        reg[0][2] = 0f
        reg[0][3] = 0f
        reg[2303][0] = 0f
        reg[2303][1] = 0f
        reg[2303][2] = 0f
        reg[2303][3] = 0f
        val out = BlazeFaceDecoder.decode(reg, cls)
        assertEquals(2, out.size)
    }
}
