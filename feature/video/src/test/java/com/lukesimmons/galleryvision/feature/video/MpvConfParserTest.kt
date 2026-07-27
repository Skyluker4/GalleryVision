// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.video

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvConfParserTest {
    @Test
    fun parsesKeyValueLines() {
        val conf = "hwdec=auto\nvideo-zoom=1.5"
        assertEquals(
            listOf("hwdec" to "auto", "video-zoom" to "1.5"),
            VideoViewModel.parseMpvConf(conf),
        )
    }

    @Test
    fun skipsCommentsAndBlankLines() {
        val conf = "# a comment\n\nhwdec=auto\n   \n  #indented comment"
        assertEquals(listOf("hwdec" to "auto"), VideoViewModel.parseMpvConf(conf))
    }

    @Test
    fun trimsWhitespaceAroundKeyAndValue() {
        assertEquals(listOf("vo" to "gpu"), VideoViewModel.parseMpvConf("  vo = gpu  "))
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        assertEquals(listOf("vf" to "eq=brightness=0.5"), VideoViewModel.parseMpvConf("vf=eq=brightness=0.5"))
    }

    @Test
    fun ignoresMalformedLines() {
        assertEquals(emptyList(), VideoViewModel.parseMpvConf("noequals\n=novalue"))
    }

    @Test
    fun emptyConfigYieldsNoOptions() {
        assertEquals(emptyList(), VideoViewModel.parseMpvConf(""))
    }
}
