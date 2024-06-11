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
    protected var modelPath: String = "models/ch_PP-OCRv2"
    protected var labelPath: String = "labels/ppocr_keys_v1.txt"
    protected var imagePath: String = "images/det_0.jpg"
    protected var cpuThreadNum: Int = 1
    protected var cpuPowerMode: String = "LITE_POWER_HIGH"
    protected var detLongSize: Int = 960
    protected var scoreThreshold: Float = 0.1f

    protected var predictor: Predictor = Predictor()

    private var cur_predict_image: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val `in` = assets.open(imagePath)
        val bmp = BitmapFactory.decodeStream(`in`)
        cur_predict_image = bmp

        if (predictor.isLoaded()) {
            predictor.releaseModel()
        }

        val success = predictor.init(
            this@MainActivity,
            modelPath,
            labelPath,
            1,
            cpuThreadNum,
            cpuPowerMode,
            detLongSize,
            scoreThreshold
        )

        println("Model loaded: $success")

        setContent {
            GalleryVisionTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
                }
            }
        }
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