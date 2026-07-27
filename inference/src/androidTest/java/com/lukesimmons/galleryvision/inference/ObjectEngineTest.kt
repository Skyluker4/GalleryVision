// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies PP-PicoDet object detection on-device: the Einstein portrait must read as "person". */
@RunWith(AndroidJUnit4::class)
class ObjectEngineTest {

    @Test
    fun detectsPerson() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bitmap = BitmapFactory.decodeStream(ctx.assets.open("face.jpg"))
        val targetCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = ObjectEngine(targetCtx)
        try {
            val objects = engine.detect(bitmap)
            println("ObjectEngineTest detected ${objects.size} objects")
            objects.forEachIndexed { i, o ->
                println("  [$i] ${o.label} ${o.score} box=(${o.left},${o.top},${o.right},${o.bottom})")
            }
            val person = objects.filter { it.label == "person" }
            assertTrue("expected a 'person' detection, got: ${objects.map { it.label }}", person.isNotEmpty())
            val top = person.maxBy { it.score }
            assertTrue("person box invalid", top.right > top.left && top.bottom > top.top &&
                top.left in 0f..1f && top.right in 0f..1f && top.top in 0f..1f && top.bottom in 0f..1f)
        } finally {
            engine.close()
        }
    }
}
