package com.lukesimmons.galleryvision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.lukesimmons.galleryvision.ui.theme.GalleryVisionTheme

class MainActivity : ComponentActivity() {
    private var modelPath: String = "models/ch_PP-OCRv2"
    private var labelPath: String = "labels/ppocr_keys_v1.txt"
    private var imagePath: String = "images/det_0.jpg"
    private var cpuThreadNum: Int = 1
    private var cpuPowerMode: String = "LITE_POWER_HIGH"
    private var detLongSize: Int = 960
    private var scoreThreshold: Float = 0.1f

    private var predictor: Predictor = Predictor()

    private var curPredictImage: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val `in` = assets.open(imagePath)
        val bmp = BitmapFactory.decodeStream(`in`)
        curPredictImage = bmp

        if (predictor.isLoaded) {
            predictor.releaseModel()
        }

        val loadSuccess = predictor.init(
            this@MainActivity,
            modelPath,
            labelPath,
            1,
            cpuThreadNum,
            cpuPowerMode,
            detLongSize,
            scoreThreshold
        )

        if (loadSuccess) {
            predictor.inputImage = curPredictImage

            val runSuccess = predictor.runModel(1, 1, 1)

            println("Model ran: $runSuccess")
            if (runSuccess) {
                println("Output:" + predictor.outputResult)
            }
        }

        setContent {
            GalleryVisionTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
                }
            }
        }
    }

    override fun onDestroy() {
        predictor.releaseModel()
        super.onDestroy()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GalleryVisionTheme {
        Greeting("Android")
    }
}