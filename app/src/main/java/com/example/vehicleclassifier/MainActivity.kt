package com.example.vehicleclassifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var modelPath: String? = null
    private val REQUEST_CODE_PERMISSIONS = 2
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Activity Result Launcher for picking a file
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                modelPath = copyFileToInternalStorage(uri)
                if (modelPath != null) {
                    findViewById<Button>(R.id.btnRunClassification).isEnabled = true
                    Snackbar.make(findViewById(R.id.btnChooseModel), "Model loaded successfully", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnChooseModel = findViewById<Button>(R.id.btnChooseModel)
        val btnRunClassification = findViewById<Button>(R.id.btnRunClassification)

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        btnChooseModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickFileLauncher.launch(intent)
        }

        btnRunClassification.setOnClickListener {
            if (modelPath != null) {
                val intent = Intent(this, ClassificationActivity::class.java)
                intent.putExtra("MODEL_PATH", modelPath)
                startActivity(intent)
            } else {
                Snackbar.make(btnRunClassification, "Please choose a TFLite model first", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted
            } else {
                Snackbar.make(findViewById(R.id.btnChooseModel), "Storage permission required", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyFileToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File.createTempFile("model", ".tflite", filesDir)
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}