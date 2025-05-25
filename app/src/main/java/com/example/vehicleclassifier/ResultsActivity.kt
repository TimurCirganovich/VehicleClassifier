package com.example.vehicleclassifier

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val totalTime = intent.getLongExtra("TOTAL_TIME", 0)
        val correctPredictions = intent.getIntExtra("CORRECT_PREDICTIONS", 0)
        val avgTime = intent.getLongExtra("AVG_TIME", 0)
        val avgMemory = intent.getIntExtra("AVG_MEMORY", 0)
        val avgMemoryMB = avgMemory / (1024.0 * 1024.0)
        val avgCpuLoad = intent.getFloatExtra("AVG_CPU", 0f)
        val inferencePerSecond = intent.getIntExtra("INFERENCE_PER_SECOND", 0)
        val totalImages = 100

        val tvResults = findViewById<TextView>(R.id.tvResults)
        tvResults.text = """
            Classification Results:
            Total Time: ${TimeUnit.MILLISECONDS.toSeconds(totalTime)}s
            Correct Predictions: $correctPredictions / $totalImages (${(correctPredictions.toFloat() / totalImages * 100).roundToInt()}%)
            Average Time per Prediction: ${TimeUnit.MILLISECONDS.toMillis(avgTime)}ms
            Average Memory Usage: ${"%.2f".format(avgMemoryMB)} MB
            Average CPU Load: ${"%.2f".format(avgCpuLoad)}%
            Inference per Second: $inferencePerSecond
        """.trimIndent()

        findViewById<Button>(R.id.btnHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }
}