// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the library grid renders media cards after a scan and opens a detail screen on tap. */
@RunWith(AndroidJUnit4::class)
class LibraryGridTest {
    @Test
    fun gridShowsMediaAfterScanAndOpensDetail() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Grant before launch so the permission gate passes on first composition.
        instrumentation.uiAutomation.grantRuntimePermission(
            APP_PACKAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            APP_PACKAGE,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        // CI emulators start with an empty MediaStore; seed one so the grid has a card.
        seedTestImage(instrumentation.targetContext)

        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            val device = UiDevice.getInstance(instrumentation)
            // Grid cards carry the media path as content-description.
            val card = device.wait(Until.findObject(By.descContains("/storage/")), SCAN_TIMEOUT_MS)
            assertNotNull("grid must show media cards after scan", card)

            card.click()
            // The library search bar (unique placeholder) disappears when a detail screen opens.
            val navigated =
                device.wait(
                    Until.gone(By.textContains("path:name*")),
                    NAV_TIMEOUT_MS,
                )
            assertTrue("tapping a card must open a detail screen", navigated)
            assertTrue(APP_PACKAGE == device.currentPackageName)
        }
    }

    private companion object {
        const val APP_PACKAGE = "com.lukesimmons.galleryvision.debug"
        const val SCAN_TIMEOUT_MS = 45_000L
        const val NAV_TIMEOUT_MS = 8_000L

        private fun seedTestImage(context: Context) {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "gv-ci-seed.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/gv-ci")
                }
            val uri =
                context.contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { it.write(out.toByteArray()) }
        }
    }
}
