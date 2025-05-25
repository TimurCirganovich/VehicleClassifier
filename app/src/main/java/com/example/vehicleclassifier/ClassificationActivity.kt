package com.example.vehicleclassifier

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import org.tensorflow.lite.DataType

class ClassificationActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private val imageLabels = listOf("BUS", "Bike", "CNG", "Easy-Bike", "Hatchback", "MPV", "Pickup", "SUV", "Sedan", "Truck")
    private var correctPredictions = 0
    private var currentImageIndex = 0
    private val totalImages = 100
    private val predictionTimes = mutableListOf<Long>()
    private val memoryUsages = mutableListOf<Long>()
    private val cpuLoads = mutableListOf<Float>()
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private val imageList = mutableListOf<Pair<String, String>>() // Pair of (subDir, imageName)
    private val inputImageSize = 224 // Matches Colab model input shape [1, 224, 224, 3]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classification)

        val modelPath = intent.getStringExtra("MODEL_PATH")
        if (modelPath == null) {
            Log.e("ClassificationActivity", "Model path is null")
            finish()
            return
        }

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val ivImage = findViewById<ImageView>(R.id.ivImage)
        val tvTrueLabel = findViewById<TextView>(R.id.tvTrueLabel)
        val tvPredictedLabel = findViewById<TextView>(R.id.tvPredictedLabel)
        val tvCorrectCount = findViewById<TextView>(R.id.tvCorrectCount)

        // Load model
        try {
            interpreter = Interpreter(File(modelPath))
            Log.i("ClassificationActivity", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("ClassificationActivity", "Failed to load TFLite model: ${e.message}", e)
            finish()
            return
        }

        // Load image list from assets
        val assetManager = assets
        val subDirs = assetManager.list("processed_test_data")?.sorted()
        if (subDirs == null || subDirs.size != 10 || subDirs != imageLabels) {
            Log.e("ClassificationActivity", "Invalid asset structure: subDirs=${subDirs?.joinToString()}")
            finish()
            return
        }

        for (subDir in subDirs) {
            val images = assetManager.list("processed_test_data/$subDir")?.filter { it.endsWith(".jpg") || it.endsWith(".jpeg") }
            if (images == null) {
                Log.e("ClassificationActivity", "No images found in $subDir")
                continue
            }
            images.forEach { imageName ->
                imageList.add(Pair(subDir, imageName))
            }
        }

        if (imageList.size != totalImages) {
            Log.e("ClassificationActivity", "Expected $totalImages images, but found ${imageList.size}")
            finish()
            return
        }

        startTime = SystemClock.elapsedRealtime()
        classifyNextImage(ivImage, tvTrueLabel, tvPredictedLabel, tvCorrectCount)
    }

    private fun classifyNextImage(ivImage: ImageView, tvTrueLabel: TextView, tvPredictedLabel: TextView, tvCorrectCount: TextView) {
        if (currentImageIndex >= totalImages) {
            showResults()
            return
        }

        val (subDir, imageName) = imageList[currentImageIndex]
        val trueLabel = subDir

        // Show image and true label
        val bitmap: Bitmap? = try {
            val inputStream = assets.open("processed_test_data/$subDir/$imageName")
            BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
        } catch (e: IOException) {
            Log.e("ClassificationActivity", "Failed to load image $subDir/$imageName: ${e.message}", e)
            null
        }

        if (bitmap == null) {
            currentImageIndex++
            handler.postDelayed({ classifyNextImage(ivImage, tvTrueLabel, tvPredictedLabel, tvCorrectCount) }, 50)
            return
        }

        ivImage.setImageBitmap(bitmap)
        ivImage.visibility = View.VISIBLE
        tvTrueLabel.text = "True Label: $trueLabel"
        tvTrueLabel.visibility = View.VISIBLE
        tvPredictedLabel.text = "Predicted Label: "
        tvPredictedLabel.visibility = View.VISIBLE
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE

        // Classify with delay for visibility
        handler.postDelayed({
            try {
                val resizedBitmap = bitmap.scale(inputImageSize, inputImageSize)
                val inputImage = TensorImage(DataType.FLOAT32)
                inputImage.load(resizedBitmap)

                Log.i("ClassificationActivity", "Input buffer size: ${inputImage.buffer.capacity()} bytes, expected: ${inputImageSize * inputImageSize * 3 * 4}")

                val start = SystemClock.elapsedRealtime()
                val output = Array(1) { FloatArray(imageLabels.size) }
                interpreter.run(inputImage.buffer, output)          //ToDo: Fail?
                val end = SystemClock.elapsedRealtime()
                predictionTimes.add(end - start)

                val prediction = imageLabels[output[0].indexOfMax()]
                tvPredictedLabel.text = "Predicted Label: $prediction"
                tvPredictedLabel.setTextColor(if (prediction == trueLabel) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())

                if (prediction == trueLabel) {
                    correctPredictions++
                    tvCorrectCount.text = "Correct Predictions: $correctPredictions"
                }

                val runtime = Runtime.getRuntime()
                memoryUsages.add(runtime.totalMemory() - runtime.freeMemory())
                cpuLoads.add(getCpuUsage())

                currentImageIndex++
                handler.postDelayed({ classifyNextImage(ivImage, tvTrueLabel, tvPredictedLabel, tvCorrectCount) }, 10)
            } catch (e: Exception) {
                Log.e("ClassificationActivity", "Error during classification: ${e.message}", e)
                currentImageIndex++
                handler.postDelayed({ classifyNextImage(ivImage, tvTrueLabel, tvPredictedLabel, tvCorrectCount) }, 1)
            }
        }, 1)
    }

    private fun showResults() {
        val totalTime = SystemClock.elapsedRealtime() - startTime
        val avgTime = predictionTimes.average().toLong()
        val avgMemory = memoryUsages.average().roundToInt()
        val avgCpuLoad = cpuLoads.average()
        val inferencePerSecond = (totalImages.toFloat() / (totalTime / 1000f)).roundToInt()

        val intent = Intent(this, ResultsActivity::class.java)
        intent.putExtra("TOTAL_TIME", totalTime)
        intent.putExtra("CORRECT_PREDICTIONS", correctPredictions)
        intent.putExtra("AVG_TIME", avgTime)
        intent.putExtra("AVG_MEMORY", avgMemory)
        intent.putExtra("AVG_CPU", avgCpuLoad)
        intent.putExtra("INFERENCE_PER_SECOND", inferencePerSecond)
        startActivity(intent)
        finish()
    }

    private fun FloatArray.indexOfMax(): Int {
        var maxIndex = 0
        var maxValue = this[0]
        for (i in 1 until size) {
            if (this[i] > maxValue) {
                maxValue = this[i]
                maxIndex = i
            }
        }
        return maxIndex
    }

    private fun getCpuUsage(): Float {
        val pid = Process.myPid()
        try {
            val reader = ProcessBuilder("top", "-n", "1", "-p", pid.toString()).start().inputStream.bufferedReader()
            val line = reader.readLine()
            reader.close()
            val cpuUsage = line?.split("%")?.getOrNull(0)?.split("\\s+".toRegex())?.last()?.toFloatOrNull() ?: 0f
            return cpuUsage
        } catch (e: Exception) {
            return 0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close()
        handler.removeCallbacksAndMessages(null)
    }
}