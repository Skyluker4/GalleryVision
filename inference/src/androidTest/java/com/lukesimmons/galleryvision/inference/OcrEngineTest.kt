// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the on-device PP-OCRv5 det+rec pipeline produces text and positioned boxes for a
 * real image. det_180.jpg is the product-label fixture the desktop parity harness read as
 * "SUPPORT PANTY HOSE" / "Collection" / "包邮".
 */
@RunWith(AndroidJUnit4::class)
class OcrEngineTest {

    @Test
    fun ocrProducesTextAndPositionedBoxes() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bitmap = BitmapFactory.decodeStream(ctx.assets.open("det_180.jpg"))
        val targetCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = OcrEngine(targetCtx)
        val regions = try {
            engine.ocr(bitmap)
        } finally {
            engine.close()
        }

        assertTrue("expected at least one text region, got 0", regions.isNotEmpty())

        val allText = regions.joinToString(" | ") { it.text }
        println("OcrEngineTest regions (${regions.size}): $allText")
        regions.forEachIndexed { i, r -> println("  [$i] conf=${r.confidence} quad=${r.quad.toList()} text='${r.text}'") }

        val matched = regions.any {
            it.text.contains("Collection", ignoreCase = true) ||
                it.text.contains("PANTY", ignoreCase = true) ||
                it.text.contains("包邮")
        }
        assertTrue("no region matched expected label text; got: $allText", matched)

        val hasValidQuad = regions.any { r ->
            r.quad.size == 8 && r.quad.all { it in 0f..1f }
        }
        assertTrue("no region had a valid normalized 4-point quad", hasValidQuad)
    }
}
