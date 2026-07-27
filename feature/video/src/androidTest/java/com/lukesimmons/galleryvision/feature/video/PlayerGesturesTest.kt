// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerGesturesTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(TestGestureActivity::class.java)

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun coords(
        x: Float,
        y: Float,
    ): PointerCoords =
        PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }

    /** Two-finger pinch from startDx to endDx around (cx, cy) via the UiAutomation channel. */
    private fun injectPinch(
        cx: Float,
        cy: Float,
        startDx: Float,
        endDx: Float,
    ) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun event(
            action: Int,
            time: Long,
            coords: Array<PointerCoords>,
            props: Array<PointerProperties>,
        ): MotionEvent =
            MotionEvent.obtain(
                downTime,
                time,
                action,
                coords.size,
                props,
                coords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )

        val props =
            arrayOf(
                PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                },
                PointerProperties().apply {
                    id = 1
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                },
            )
        val index1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT

        automation.injectInputEvent(
            event(MotionEvent.ACTION_DOWN, downTime, arrayOf(coords(cx - startDx, cy)), props),
            true,
        )
        automation.injectInputEvent(
            event(
                MotionEvent.ACTION_POINTER_DOWN or index1,
                downTime + 10,
                arrayOf(coords(cx - startDx, cy), coords(cx + startDx, cy)),
                props,
            ),
            true,
        )
        val steps = 10
        for (i in 1..steps) {
            val d = startDx + (endDx - startDx) * i / steps
            automation.injectInputEvent(
                event(
                    MotionEvent.ACTION_MOVE,
                    downTime + 10 + i * 20L,
                    arrayOf(coords(cx - d, cy), coords(cx + d, cy)),
                    props,
                ),
                true,
            )
        }
        val upTime = downTime + 10 + steps * 20L
        automation.injectInputEvent(
            event(
                MotionEvent.ACTION_POINTER_UP or index1,
                upTime,
                arrayOf(coords(cx - endDx, cy), coords(cx + endDx, cy)),
                props,
            ),
            true,
        )
        automation.injectInputEvent(
            event(MotionEvent.ACTION_UP, upTime + 10, arrayOf(coords(cx - endDx, cy)), props),
            true,
        )
        device.waitForIdle()
    }

    private fun assertOnActivity(check: (TestGestureActivity) -> Unit) {
        activityRule.scenario.onActivity(check)
    }

    private fun awaitReady() {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            assertOnActivity { ready = it.ready }
            if (!ready) SystemClock.sleep(50)
        }
        check(ready) { "TestGestureActivity never composed its gesture host" }
    }

    @Test
    fun pinchOutCallsZoomWithFactorAboveOne() {
        awaitReady()
        val cx = device.displayWidth / 2f
        val cy = device.displayHeight / 2f
        injectPinch(cx, cy, startDx = 100f, endDx = 220f)
        assertOnActivity { a ->
            assertTrue("zoom must be called on pinch", a.zoomCount > 0)
            assertTrue("pinch out must zoom in, got ${a.zoomFactor}", a.zoomFactor > 1f)
        }
    }

    @Test
    fun pinchInCallsZoomWithFactorBelowOne() {
        awaitReady()
        val cx = device.displayWidth / 2f
        val cy = device.displayHeight / 2f
        injectPinch(cx, cy, startDx = 220f, endDx = 60f)
        assertOnActivity { a ->
            assertTrue(a.zoomCount > 0)
            assertTrue("pinch in must zoom out, got ${a.zoomFactor}", a.zoomFactor < 1f)
        }
    }

    @Test
    fun horizontalDragSeeksAndEnds() {
        awaitReady()
        val w = device.displayWidth.toFloat()
        val h = device.displayHeight.toFloat()
        device.drag((w * 0.2f).toInt(), (h * 0.5f).toInt(), (w * 0.8f).toInt(), (h * 0.5f).toInt(), 30)
        device.waitForIdle()
        assertOnActivity { a ->
            assertTrue(a.seekStarted)
            assertTrue(a.seekEnded)
            assertTrue("rightward drag must seek forward, got ${a.seekDeltaMs}", a.seekDeltaMs > 0)
        }
    }

    @Test
    fun verticalDragOnRightSideAdjustsVolume() {
        awaitReady()
        val w = device.displayWidth.toFloat()
        val h = device.displayHeight.toFloat()
        device.drag((w * 0.85f).toInt(), (h * 0.7f).toInt(), (w * 0.85f).toInt(), (h * 0.3f).toInt(), 30)
        device.waitForIdle()
        assertOnActivity { a ->
            assertTrue("upward right drag must raise volume, got ${a.volume}", a.volume > 0f)
            assertEquals(0f, a.brightness, 0.001f)
        }
    }

    @Test
    fun verticalDragOnLeftSideAdjustsBrightness() {
        awaitReady()
        val w = device.displayWidth.toFloat()
        val h = device.displayHeight.toFloat()
        device.drag((w * 0.15f).toInt(), (h * 0.7f).toInt(), (w * 0.15f).toInt(), (h * 0.3f).toInt(), 30)
        device.waitForIdle()
        assertOnActivity { a ->
            assertTrue("upward left drag must raise brightness, got ${a.brightness}", a.brightness > 0f)
            assertEquals(0f, a.volume, 0.001f)
        }
    }
}
