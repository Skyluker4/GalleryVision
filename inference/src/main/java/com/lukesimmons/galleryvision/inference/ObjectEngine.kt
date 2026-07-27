// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * On-device PP-PicoDet-S 320 object detection (Apache-2.0, COCO-80) via ONNX Runtime (XNNPACK,
 * no NNAPI). Uses the postprocessed export, so NMS is baked into the model — the engine only
 * filters by score. Boxes are normalized to [0,1] of the source image.
 */
class ObjectEngine(context: Context) {

    data class Detected(
        val label: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val score: Float,
    )

    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Volatile
    private var closed = false

    @Synchronized
    private fun ensureLoaded() {
        if (session == null) {
            val opts = OrtSession.SessionOptions().apply { addXnnpack(emptyMap()) }
            session = env.createSession(appContext.assets.open("$MODEL_DIR/picodet.onnx").readBytes(), opts)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        session?.close()
        session = null
    }

    fun detect(src: Bitmap): List<Detected> {
        ensureLoaded()
        val resized = Bitmap.createScaledBitmap(src, INPUT, INPUT, true)
        val imageTensor = toTensor(resized)
        val sfValues = FloatBuffer.allocate(2)
        sfValues.put(INPUT.toFloat() / src.height)
        sfValues.put(INPUT.toFloat() / src.width)
        sfValues.rewind()
        val sfTensor = OnnxTensor.createTensor(env, sfValues, longArrayOf(1, 2))
        resized.recycle()
        imageTensor.use { img ->
            sfTensor.use { sf ->
                session!!.run(mapOf("image" to img, "scale_factor" to sf)).use { result ->
                    val dets = out2d(result, "multiclass_nms3_0.tmp_0")
                    return decode(dets, src.width, src.height)
                }
            }
        }
    }

    private fun toTensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val fb = FloatBuffer.allocate(3 * w * h)
        for (c in 0..2) {
            val mean = MEAN[c]
            val std = STD[c]
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    val v = when (c) {
                        0 -> (p shr 16) and 0xff
                        1 -> (p shr 8) and 0xff
                        else -> p and 0xff
                    }
                    fb.put((v - mean) / std)
                }
            }
        }
        fb.rewind()
        return OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    private fun out2d(result: OrtSession.Result, name: String): Array<FloatArray> {
        for (entry in result) {
            if (entry.key == name) {
                @Suppress("UNCHECKED_CAST")
                return (entry.value as OnnxTensor).value as Array<FloatArray>
            }
        }
        throw IllegalArgumentException("PicoDet output '$name' missing")
    }

    private fun decode(dets: Array<FloatArray>, origW: Int, origH: Int): List<Detected> {
        val out = ArrayList<Detected>()
        for (row in dets) {
            if (row.size < 6) continue
            val classId = row[0].toInt()
            val score = row[1]
            if (score < SCORE_THRESH || classId < 0 || classId >= COCO80.size) continue
            val left = (row[2] / origW).coerceIn(0f, 1f)
            val top = (row[3] / origH).coerceIn(0f, 1f)
            val right = (row[4] / origW).coerceIn(0f, 1f)
            val bottom = (row[5] / origH).coerceIn(0f, 1f)
            if (right > left && bottom > top) {
                out.add(Detected(COCO80[classId], left, top, right, bottom, score))
            }
        }
        return out
    }

    companion object {
        private const val MODEL_DIR = "models/object"
        private const val INPUT = 320
        private const val SCORE_THRESH = 0.5f
        private val MEAN = floatArrayOf(103.53f, 116.28f, 123.675f)
        private val STD = floatArrayOf(57.375f, 57.12f, 58.395f)
        private val COCO80 = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush",
        )
    }
}
