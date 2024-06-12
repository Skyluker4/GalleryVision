package com.lukesimmons.galleryvision

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class OCRPredictorNative(private val config: Config) {
    private var nativePointer: Long = 0

    init {
        loadLibrary()
        nativePointer = init(
            config.detModelFilename,
            config.recModelFilename,
            config.clsModelFilename,
            config.useOpencl,
            config.cpuThreadNum,
            config.cpuPower
        )
        Log.i("OCRPredictorNative", "load success $nativePointer")
    }


    fun runImage(
        originalImage: Bitmap?,
        max_size_len: Int,
        run_det: Int,
        run_cls: Int,
        run_rec: Int
    ): ArrayList<OcrResultModel> {
        Log.i("OCRPredictorNative", "begin to run image ")
        val rawResults =
            forward(nativePointer, originalImage, max_size_len, run_det, run_cls, run_rec)
        val results = postprocess(rawResults)
        return results
    }

    class Config {
        var useOpencl: Int = 0
        var cpuThreadNum: Int = 0
        var cpuPower: String? = null
        var detModelFilename: String? = null
        var recModelFilename: String? = null
        var clsModelFilename: String? = null
    }

    fun destory() {
        if (nativePointer != 0L) {
            release(nativePointer)
            nativePointer = 0
        }
    }

    protected external fun init(
        detModelPath: String?,
        recModelPath: String?,
        clsModelPath: String?,
        useOpencl: Int,
        threadNum: Int,
        cpuMode: String?
    ): Long

    protected external fun forward(
        pointer: Long,
        originalImage: Bitmap?,
        max_size_len: Int,
        run_det: Int,
        run_cls: Int,
        run_rec: Int
    ): FloatArray

    protected external fun release(pointer: Long)

    private fun postprocess(raw: FloatArray): ArrayList<OcrResultModel> {
        val results = ArrayList<OcrResultModel>()
        var begin = 0

        while (begin < raw.size) {
            val point_num = Math.round(raw[begin])
            val word_num = Math.round(raw[begin + 1])
            val res = parse(raw, begin + 2, point_num, word_num)
            begin += 2 + 1 + point_num * 2 + word_num + 2
            results.add(res)
        }

        return results
    }

    private fun parse(raw: FloatArray, begin: Int, pointNum: Int, wordNum: Int): OcrResultModel {
        var current = begin
        val res = OcrResultModel()
        res.confidence = raw[current]
        current++
        for (i in 0 until pointNum) {
            res.addPoints(Math.round(raw[current + i * 2]), Math.round(raw[current + i * 2 + 1]))
        }
        current += (pointNum * 2)
        for (i in 0 until wordNum) {
            val index = Math.round(raw[current + i])
            res.addWordIndex(index)
        }
        current += wordNum
        res.clsIdx = raw[current]
        res.clsConfidence = raw[current + 1]
        Log.i("OCRPredictorNative", "word finished $wordNum")
        return res
    }


    companion object {
        private val isSOLoaded = AtomicBoolean()

        @Throws(RuntimeException::class)
        fun loadLibrary() {
            if (!isSOLoaded.get() && isSOLoaded.compareAndSet(false, true)) {
                try {
                    System.loadLibrary("Native")
                } catch (e: Throwable) {
                    val exception = RuntimeException(
                        "Load libNative.so failed, please check it exists in apk file.", e
                    )
                    throw exception
                }
            }
        }
    }
}
