package com.lukesimmons.galleryvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import java.io.File
import java.util.Date
import java.util.Vector
import kotlin.concurrent.Volatile

class Predictor {
    var isLoaded: Boolean = false
        get() {
            return paddlePredictor != null && field
        }
    var warmupIterNum: Int = 1
    var inferIterNum: Int = 1
    var cpuThreadNum: Int = 4
    var cpuPowerMode: String = "LITE_POWER_HIGH"
    var modelPath: String = ""
    var modelName: String = ""
    protected var paddlePredictor: OCRPredictorNative? = null
    protected var inferenceTime: Float = 0f

    // Only for object detection
    protected var wordLabels: Vector<String> = Vector()
    protected var detLongSize: Int = 960
    protected var scoreThreshold: Float = 0.1f
    var inputImage: Bitmap? = null
        set(image) {
            if (image == null) {
                return
            }
            field = image.copy(Bitmap.Config.ARGB_8888, true)
        }
    protected var outputImage: Bitmap? = null

    @Volatile
    protected var outputResult: String = ""
    protected var postprocessTime: Float = 0f


    fun init(
        appCtx: Context,
        modelPath: String,
        labelPath: String?,
        useOpencl: Int,
        cpuThreadNum: Int,
        cpuPowerMode: String
    ): Boolean {
        isLoaded = loadModel(appCtx, modelPath, useOpencl, cpuThreadNum, cpuPowerMode)
        if (!isLoaded) {
            return false
        }
        isLoaded = loadLabel(appCtx, labelPath)
        return isLoaded
    }


    fun init(
        appCtx: Context,
        modelPath: String,
        labelPath: String?,
        useOpencl: Int,
        cpuThreadNum: Int,
        cpuPowerMode: String,
        detLongSize: Int,
        scoreThreshold: Float
    ): Boolean {
        val isLoaded = init(appCtx, modelPath, labelPath, useOpencl, cpuThreadNum, cpuPowerMode)
        if (!isLoaded) {
            return false
        }
        this.detLongSize = detLongSize
        this.scoreThreshold = scoreThreshold
        return true
    }

    protected fun loadModel(
        appCtx: Context,
        modelPath: String,
        useOpencl: Int,
        cpuThreadNum: Int,
        cpuPowerMode: String
    ): Boolean {
        // Release model if exists
        releaseModel()

        // Load model
        if (modelPath.isEmpty()) {
            return false
        }
        var realPath = modelPath
        if (modelPath.substring(0, 1) != "/") {
            // Read model files from custom path if the first character of mode path is '/'
            // otherwise copy model to cache from assets
            realPath = appCtx.cacheDir.toString() + "/" + modelPath
            Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath)
        }
        if (realPath.isEmpty()) {
            return false
        }

        val config = OCRPredictorNative.Config()
        config.useOpencl = useOpencl
        config.cpuThreadNum = cpuThreadNum
        config.cpuPower = cpuPowerMode
        config.detModelFilename = realPath + File.separator + "det_db.nb"
        config.recModelFilename = realPath + File.separator + "rec_crnn.nb"
        config.clsModelFilename = realPath + File.separator + "cls.nb"
        Log.i(
            "Predictor",
            "model path" + config.detModelFilename + " ; " + config.recModelFilename + ";" + config.clsModelFilename
        )
        paddlePredictor = OCRPredictorNative(config)

        this.cpuThreadNum = cpuThreadNum
        this.cpuPowerMode = cpuPowerMode
        this.modelPath = realPath
        this.modelName = realPath.substring(realPath.lastIndexOf("/") + 1)
        return true
    }

    fun releaseModel() {
        if (paddlePredictor != null) {
            paddlePredictor!!.destory()
            paddlePredictor = null
        }
        isLoaded = false
        cpuThreadNum = 1
        cpuPowerMode = "LITE_POWER_HIGH"
        modelPath = ""
        modelName = ""
    }

    protected fun loadLabel(appCtx: Context, labelPath: String?): Boolean {
        wordLabels.clear()
        wordLabels.add("black")
        // Load word labels from file
        try {
            val assetsInputStream = appCtx.assets.open(labelPath!!)
            val available = assetsInputStream.available()
            val lines = ByteArray(available)
            assetsInputStream.read(lines)
            assetsInputStream.close()
            val words = String(lines)
            val contents = words.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (content in contents) {
                wordLabels.add(content)
            }
            wordLabels.add(" ")
            Log.i(TAG, "Word label size: " + wordLabels.size)
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            return false
        }
        return true
    }


    fun runModel(run_det: Int, run_cls: Int, run_rec: Int): Boolean {
        if (inputImage == null || !isLoaded) {
            return false
        }

        // Warm up
        for (i in 0 until warmupIterNum) {
            paddlePredictor!!.runImage(inputImage, detLongSize, run_det, run_cls, run_rec)
        }
        warmupIterNum = 0 // do not need warm
        // Run inference
        val start = Date()
        var results = paddlePredictor!!.runImage(inputImage, detLongSize, run_det, run_cls, run_rec)
        val end = Date()
        inferenceTime = (end.time - start.time) / inferIterNum.toFloat()

        results = postprocess(results)
        Log.i(TAG, "[stat] Inference Time: " + inferenceTime + " ;Box Size " + results.size)
        drawResults(results)

        return true
    }

    fun modelPath(): String {
        return modelPath
    }

    fun modelName(): String {
        return modelName
    }

    fun cpuThreadNum(): Int {
        return cpuThreadNum
    }

    fun cpuPowerMode(): String {
        return cpuPowerMode
    }

    fun inferenceTime(): Float {
        return inferenceTime
    }

    fun inputImage(): Bitmap? {
        return inputImage
    }

    fun outputImage(): Bitmap? {
        return outputImage
    }

    fun outputResult(): String {
        return outputResult
    }

    fun postprocessTime(): Float {
        return postprocessTime
    }

    private fun postprocess(results: ArrayList<OcrResultModel>): ArrayList<OcrResultModel> {
        for (r in results!!) {
            val word = StringBuffer()
            for (index in r!!.getWordIndex()) {
                if (index >= 0 && index < wordLabels.size) {
                    word.append(wordLabels[index])
                } else {
                    Log.e(TAG, "Word index is not in label list:$index")
                    word.append("×")
                }
            }
            r.label = word.toString()
            r.clsLabel = if (r.clsIdx == 1f) "180" else "0"
        }
        return results
    }

    private fun drawResults(results: ArrayList<OcrResultModel>) {
        val outputResultSb = StringBuffer("")
        for (i in results!!.indices) {
            val result = results[i]
            val sb = StringBuilder("")
            if (result!!.getPoints().size > 0) {
                sb.append("Det: ")
                for (p in result.getPoints()) {
                    sb.append("(").append(p!!.x).append(",").append(p.y).append(") ")
                }
            }
            if ((result.label?.length ?: 0) > 0) {
                sb.append("\n Rec: ").append(result.label)
                sb.append(",").append(result.confidence)
            }
            if (result.clsIdx != -1f) {
                sb.append(" Cls: ").append(result.clsLabel)
                sb.append(",").append(result.clsConfidence)
            }
            Log.i(TAG, sb.toString()) // show LOG in Logcat panel
            outputResultSb.append(i + 1).append(": ").append(sb.toString()).append("\n")
        }
        outputResult = outputResultSb.toString()
        outputImage = inputImage
        val canvas = Canvas(outputImage!!)
        val paintFillAlpha = Paint()
        paintFillAlpha.style = Paint.Style.FILL
        paintFillAlpha.color = Color.parseColor("#3B85F5")
        paintFillAlpha.alpha = 50

        val paint = Paint()
        paint.color = Color.parseColor("#3B85F5")
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE

        for (result in results) {
            val path = Path()
            val points = result!!.getPoints()
            if (points!!.size == 0) {
                continue
            }
            path.moveTo(points[0]!!.x.toFloat(), points[0]!!.y.toFloat())
            for (i in points.indices.reversed()) {
                val p = points[i]
                path.lineTo(p!!.x.toFloat(), p.y.toFloat())
            }
            canvas.drawPath(path, paint)
            canvas.drawPath(path, paintFillAlpha)
        }
    }

    companion object {
        private val TAG: String = Predictor::class.java.simpleName
    }
}
