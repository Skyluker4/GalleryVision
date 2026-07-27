// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.index

import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.core.database.entity.FaceClusterEntity
import kotlin.math.sqrt

/** Groups FACE detections into person clusters by SFace embedding cosine similarity. */
class FaceClusterer(private val db: GalleryVisionDatabase) {

    suspend fun recluster(similarityThreshold: Float = DEFAULT_THRESHOLD): Int {
        val detections = db.detectionDao().facesWithEmbeddings()
        val items = detections.mapNotNull { det ->
            parseEmbedding(det.embedding)?.let { det.id to it }
        }
        if (items.isEmpty()) return 0

        val assignments = cluster(items.map { it.second }, similarityThreshold)

        db.faceClusterDao().clearAll()
        val indexToClusterId = mutableMapOf<Int, Long>()
        for ((i, det) in items.withIndex()) {
            val clusterIndex = assignments[i]
            val clusterId = indexToClusterId.getOrPut(clusterIndex) {
                db.faceClusterDao().upsert(FaceClusterEntity(name = null, contactLookupKey = null))
            }
            db.detectionDao().assignCluster(det.first, clusterId)
        }
        return indexToClusterId.size
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.55f

        /**
         * Greedy incremental-centroid clustering. Each embedding joins the cluster whose centroid is
         * most similar if that similarity meets [threshold], else it starts a new cluster. Returns a
         * cluster index per embedding (same order as the input).
         */
        fun cluster(embeddings: List<FloatArray>, threshold: Float): List<Int> {
            val centroids = mutableListOf<FloatArray>()
            val assignments = IntArray(embeddings.size)
            for ((i, emb) in embeddings.withIndex()) {
                var bestIdx = -1
                var bestSim = -1f
                for ((idx, centroid) in centroids.withIndex()) {
                    val sim = cosine(emb, centroid)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestIdx = idx
                    }
                }
                if (bestIdx >= 0 && bestSim >= threshold) {
                    assignments[i] = bestIdx
                    val centroid = centroids[bestIdx]
                    for (d in centroid.indices) centroid[d] = (centroid[d] + emb[d]) / 2f
                } else {
                    centroids.add(emb.copyOf())
                    assignments[i] = centroids.size - 1
                }
            }
            return assignments.toList()
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var na = 0f
            var nb = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
        }

        fun parseEmbedding(csv: String?): FloatArray? =
            csv?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }?.toFloatArray()
    }
}
