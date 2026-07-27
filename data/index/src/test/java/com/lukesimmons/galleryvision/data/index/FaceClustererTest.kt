// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.index

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceClustererTest {

    @Test
    fun cosineIdenticalIsOne() {
        assertEquals(1f, FaceClusterer.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f, 3f)), 0.001f)
    }

    @Test
    fun cosineOppositeIsNegativeOne() {
        assertEquals(-1f, FaceClusterer.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 0.001f)
    }

    @Test
    fun cosineOrthogonalIsZero() {
        assertEquals(0f, FaceClusterer.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.001f)
    }

    @Test
    fun clusterGroupsSimilarEmbeddings() {
        val a = floatArrayOf(1f, 0f, 0f)
        val aPrime = floatArrayOf(0.99f, 0.01f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val assignments = FaceClusterer.cluster(listOf(a, aPrime, b), 0.9f)
        assertEquals(assignments[0], assignments[1], "near-identical embeddings must share a cluster")
        assertTrue(assignments[2] != assignments[0], "opposite embedding must start its own cluster")
    }

    @Test
    fun clusterRespectsThreshold() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0.7f, 0.7f) // cosine(a, b) ~= 0.707
        val strict = FaceClusterer.cluster(listOf(a, b), 0.9f)
        val loose = FaceClusterer.cluster(listOf(a, b), 0.5f)
        assertTrue(strict[0] != strict[1], "below-threshold pair must split")
        assertEquals(loose[0], loose[1], "above-threshold pair must join")
    }

    @Test
    fun parseEmbeddingRoundTrip() {
        val parsed = FaceClusterer.parseEmbedding("1.0,0.5,-0.25")
        assertEquals(listOf(1f, 0.5f, -0.25f), parsed?.toList())
        assertEquals(null, FaceClusterer.parseEmbedding(null))
    }
}
