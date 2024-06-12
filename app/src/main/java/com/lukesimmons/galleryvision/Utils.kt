package com.lukesimmons.galleryvision

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Utils {
    private fun copyFileFromAssets(appCtx: Context, srcPath: String, dstPath: String) {
        if (srcPath.isEmpty() || dstPath.isEmpty()) {
            return
        }
        var `is`: InputStream? = null
        var os: OutputStream? = null
        try {
            `is` = BufferedInputStream(appCtx.assets.open(srcPath))
            os = BufferedOutputStream(FileOutputStream(File(dstPath)))
            val buffer = ByteArray(1024)
            var length: Int
            while ((`is`.read(buffer).also { length = it }) != -1) {
                os.write(buffer, 0, length)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                os!!.close()
                `is`!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun copyDirectoryFromAssets(appCtx: Context, srcDir: String, dstDir: String) {
        if (srcDir.isEmpty() || dstDir.isEmpty()) {
            return
        }
        try {
            if (!File(dstDir).exists()) {
                File(dstDir).mkdirs()
            }
            for (fileName in appCtx.assets.list(srcDir)!!) {
                val srcSubPath = srcDir + File.separator + fileName
                val dstSubPath = dstDir + File.separator + fileName
                if (File(srcSubPath).isDirectory) {
                    copyDirectoryFromAssets(appCtx, srcSubPath, dstSubPath)
                } else {
                    copyFileFromAssets(appCtx, srcSubPath, dstSubPath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
