// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    private fun polygonArea(quad: FloatArray): Float {
        var area = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += quad[i * 2] * quad[j * 2 + 1] - quad[j * 2] * quad[i * 2 + 1]
        }
        return kotlin.math.abs(area) / 2f
    }

    @Test
    fun hullOfSquareWithInteriorPointIsTheSquare() {
        val hull = Geometry.convexHull(
            floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 5f, 5f, 5f, 5f),
        )
        assertEquals(8, hull.size)
    }

    @Test
    fun hullOfTwoPointsIsDegenerate() {
        val hull = Geometry.convexHull(floatArrayOf(1f, 2f, 3f, 4f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), hull)
    }

    @Test
    fun hullOfCollinearPointsDropsTheMiddle() {
        val hull = Geometry.convexHull(floatArrayOf(0f, 0f, 5f, 5f, 10f, 10f))
        assertEquals(4, hull.size)
    }

    @Test
    fun minAreaRectOfAxisAlignedSquareHasSquareArea() {
        val rect = Geometry.minAreaRect(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f))
        assertEquals(100f, polygonArea(rect), 1f)
    }

    @Test
    fun minAreaRectRecoversRotatedRectangleArea() {
        // A 20x10 rectangle rotated 45 degrees: the min-area rect must match its true area.
        val pts = floatArrayOf(
            0f, 0f,
            20f * COS45, 20f * SIN45,
            20f * COS45 - 10f * SIN45, 20f * SIN45 + 10f * COS45,
            -10f * SIN45, 10f * COS45,
        )
        val hull = Geometry.convexHull(pts)
        val rect = Geometry.minAreaRect(hull)
        assertEquals(200f, polygonArea(rect), 2f)
    }

    @Test
    fun minAreaRectOfTwoPointsPadsToFourCorners() {
        val rect = Geometry.minAreaRect(floatArrayOf(1f, 2f, 3f, 4f))
        assertEquals(8, rect.size)
        assertEquals(1f, rect[0])
        assertEquals(2f, rect[1])
    }

    @Test
    fun orientHorizontalKeepsWideQuad() {
        val wide = floatArrayOf(0f, 0f, 20f, 0f, 20f, 5f, 0f, 5f)
        assertContentEquals(wide, Geometry.orientHorizontal(wide))
    }

    @Test
    fun orientHorizontalRotatesTallQuad() {
        val tall = floatArrayOf(0f, 0f, 5f, 0f, 5f, 20f, 0f, 20f)
        val out = Geometry.orientHorizontal(tall)
        assertContentEquals(floatArrayOf(5f, 0f, 5f, 20f, 0f, 20f, 0f, 0f), out)
    }

    @Test
    fun expandByOneIsIdentity() {
        val quad = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertContentEquals(quad, Geometry.expand(quad, 1f))
    }

    @Test
    fun expandByTwoDoublesDistanceFromCentroid() {
        val quad = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val out = Geometry.expand(quad, 2f)
        assertEquals(-5f, out[0], 1e-4f)
        assertEquals(-5f, out[1], 1e-4f)
        assertEquals(15f, out[4], 1e-4f)
        assertEquals(15f, out[5], 1e-4f)
        val original = sqrt(50f)
        val expanded = sqrt((out[0] - 5f) * (out[0] - 5f) + (out[1] - 5f) * (out[1] - 5f))
        assertEquals(original * 2f, expanded, 1e-3f)
    }

    private companion object {
        const val COS45 = 0.70710677f
        const val SIN45 = 0.70710677f
    }
}
