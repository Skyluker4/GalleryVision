// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.mediastore

import java.io.File

/**
 * Content sniffing for animated still formats. MediaStore reports APNG files as
 * image/png (and animated WebP as image/webp), so animation can only be told from
 * the container chunks: acTL (APNG, before the first IDAT) and ANIM/ANMF (WebP RIFF).
 */
object AnimatedSniff {
    fun isApng(
        header: ByteArray,
        length: Int,
    ): Boolean {
        val acTL = indexOfMagic(header, length, "acTL")
        if (acTL < 0) return false
        val idat = indexOfMagic(header, length, "IDAT")
        return idat < 0 || acTL < idat
    }

    fun isAnimatedWebP(
        header: ByteArray,
        length: Int,
    ): Boolean = indexOfMagic(header, length, "ANIM") >= 0

    fun readHeader(
        path: String,
        maxBytes: Int = SNIFF_BYTES,
    ): Pair<ByteArray, Int>? =
        try {
            File(path).inputStream().use { s ->
                val buf = ByteArray(maxBytes)
                val n = s.read(buf)
                if (n > 0) buf to n else null
            }
        } catch (e: Exception) {
            null
        }

    fun indexOfMagic(
        buf: ByteArray,
        length: Int,
        magic: String,
    ): Int {
        val m = magic.toByteArray(Charsets.US_ASCII)
        if (length < m.size) return -1
        outer@ for (i in 0..length - m.size) {
            for (j in m.indices) {
                if (buf[i + j] != m[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private const val SNIFF_BYTES = 256
}
