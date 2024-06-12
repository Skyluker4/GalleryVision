package com.lukesimmons.galleryvision

import android.graphics.Point

class OcrResultModel {
    private val points: MutableList<Point> = ArrayList()
    private val wordIndex: MutableList<Int> = ArrayList()
    var label: String? = null
    var confidence: Float = 0f
    var clsIdx: Float = 0f
    var clsLabel: String? = null
    var clsConfidence: Float = 0f

    fun addPoints(x: Int, y: Int) {
        val point = Point(x, y)
        points.add(point)
    }

    fun addWordIndex(index: Int) {
        wordIndex.add(index)
    }

    fun getPoints(): List<Point> {
        return points
    }

    fun getWordIndex(): List<Int> {
        return wordIndex
    }
}
