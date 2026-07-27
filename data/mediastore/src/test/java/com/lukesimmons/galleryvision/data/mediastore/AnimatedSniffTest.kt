// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.mediastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedSniffTest {

    private fun png(vararg chunks: String): ByteArray {
        val sig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        return sig + chunks.joinToString("").toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun apngWithActlBeforeIdatIsAnimated() {
        val data = png("IHDR....", "acTL........", "IDAT....")
        assertTrue(AnimatedSniff.isApng(data, data.size))
    }

    @Test
    fun pngWithoutActlIsStatic() {
        val data = png("IHDR....", "IDAT....")
        assertFalse(AnimatedSniff.isApng(data, data.size))
    }

    @Test
    fun actlAfterIdatIsNotApng() {
        val data = png("IHDR....", "IDAT....", "acTL....")
        assertFalse(AnimatedSniff.isApng(data, data.size))
    }

    @Test
    fun truncatedHeaderIsStatic() {
        val data = byteArrayOf(-119, 80, 78)
        assertFalse(AnimatedSniff.isApng(data, data.size))
    }

    @Test
    fun webpWithAnimChunkIsAnimated() {
        val data = "RIFF....WEBPVP8X....ANIM........".toByteArray(Charsets.US_ASCII)
        assertTrue(AnimatedSniff.isAnimatedWebP(data, data.size))
    }

    @Test
    fun webpWithAnmfButNoAnimChunkIsNotDetected() {
        // Per spec the ANIM chunk is mandatory in animated WebP; ANMF alone is invalid.
        val data = "RIFF....WEBPVP8L....ANMF........".toByteArray(Charsets.US_ASCII)
        assertFalse(AnimatedSniff.isAnimatedWebP(data, data.size))
    }

    @Test
    fun staticWebpIsNotAnimated() {
        val data = "RIFF....WEBPVP8 ........".toByteArray(Charsets.US_ASCII)
        assertFalse(AnimatedSniff.isAnimatedWebP(data, data.size))
    }

    @Test
    fun indexOfMagicFindsAndMisses() {
        val buf = "0123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(4, AnimatedSniff.indexOfMagic(buf, buf.size, "456"))
        assertEquals(-1, AnimatedSniff.indexOfMagic(buf, buf.size, "abc"))
        assertEquals(-1, AnimatedSniff.indexOfMagic(buf, 3, "456"))
        assertEquals(-1, AnimatedSniff.indexOfMagic(buf, buf.size, "01234567890"))
    }
}
