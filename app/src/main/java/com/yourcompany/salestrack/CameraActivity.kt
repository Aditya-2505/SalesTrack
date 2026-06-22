package com.yourcompany.salestrack

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yourcompany.salestrack.databinding.ActivityCameraBinding
import com.yourcompany.salestrack.gemini.GeminiClient
import com.yourcompany.salestrack.supabase.StorageRepository
import com.yourcompany.salestrack.supabase.TripsRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val geminiClient = GeminiClient()
    private val tripsRepository = TripsRepository()
    private val storageRepository = StorageRepository()

    private var tripType: String = "out"
    private var userId: String = ""
    private var capturedImageBytes: ByteArray? = null

    private var lastReading = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tripType = intent.getStringExtra("trip_type") ?: "out"

        val sharedPref = getSharedPreferences("SalesTrackSession", Context.MODE_PRIVATE)
        userId = sharedPref.getString("user_id", "") ?: ""

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Fetch last reading for validation check
        lifecycleScope.launch {
            lastReading = tripsRepository.getLastOdometerReading(userId)
        }

        binding.btnCameraBack.setOnClickListener {
            finish()
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // Setup manual save button
        binding.btnSaveManual.setOnClickListener {
            val manualInput = binding.edtManualKm.text.toString().trim()
            if (manualInput.isEmpty()) {
                Toast.makeText(this, "Please enter KM reading", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val km = manualInput.toIntOrNull()
            if (km == null) {
                Toast.makeText(this, "Invalid entry", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            validateAndSave(km)
        }

        // Setup confirm button
        binding.btnConfirm.setOnClickListener {
            val detectedKm = binding.txtDetectedKm.text.toString().toIntOrNull()
            if (detectedKm != null) {
                validateAndSave(detectedKm)
            }
        }

        // Setup edit manually button on verification card
        binding.btnEditManually.setOnClickListener {
            binding.cardVerification.visibility = View.GONE
            binding.cardManualInput.visibility = View.VISIBLE
            binding.edtManualKm.setText(binding.txtDetectedKm.text)
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create a temporary file to hold the captured photo
        val photoFile = File(cacheDir, "temp_odo_capture.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.btnCapture.isEnabled = false
        binding.layoutProcessing.visibility = View.VISIBLE

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processImage(photoFile)
                }

                override fun onError(exc: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    binding.layoutProcessing.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processImage(photoFile: File) {
        lifecycleScope.launch {
            try {
                // Read bytes
                val rawBytes = photoFile.readBytes()
                
                // Compress image to save bandwidth and stay well within Gemini API limits
                val compressedBytes = compressImage(rawBytes)
                capturedImageBytes = compressedBytes
                
                // Show thumbnail
                val bitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                binding.imgPhotoThumbnail.setImageBitmap(bitmap)

                // Call Gemini
                val result = geminiClient.extractOdometerReading(compressedBytes)

                binding.layoutProcessing.visibility = View.GONE
                
                val cleanedResult = result.replace(Regex("[^0-9]"), "") // Remove non-numeric characters if any
                val detectedKm = cleanedResult.toIntOrNull()

                if (detectedKm != null && cleanedResult.isNotEmpty()) {
                    binding.cardVerification.visibility = View.VISIBLE
                    binding.txtDetectedKm.text = cleanedResult
                } else {
                    // Gemini returned "UNCLEAR" or text not parsing to number
                    binding.cardManualInput.visibility = View.VISIBLE
                    binding.txtManualReason.text = "Gemini could not read the speedometer photo confidently. Please enter the KM manually."
                }
            } catch (e: Exception) {
                binding.layoutProcessing.visibility = View.GONE
                binding.cardManualInput.visibility = View.VISIBLE
                binding.txtManualReason.text = "Error processing image. Please enter KM manually."
            } finally {
                // Clean up raw temp file
                if (photoFile.exists()) {
                    photoFile.delete()
                }
            }
        }
    }

    private fun compressImage(rawBytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        
        // Scale down to a reasonable max dimension (e.g. 1024 width/height) to save upload bytes
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height
        
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension * height) / width
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * width) / height
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return baos.toByteArray()
    }

    private fun validateAndSave(kmReading: Int) {
        // Validation check: must be greater than previous
        if (lastReading > 0 && kmReading <= lastReading) {
            Toast.makeText(
                this,
                "This reading is lower than your last entry ($lastReading KM). Please check the photo or enter the correct number.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        binding.btnConfirm.isEnabled = false
        binding.btnSaveManual.isEnabled = false
        binding.layoutProcessing.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val imageBytes = capturedImageBytes
                if (imageBytes == null) {
                    Toast.makeText(this@CameraActivity, "No photo captured", Toast.LENGTH_SHORT).show()
                    binding.layoutProcessing.visibility = View.GONE
                    binding.btnConfirm.isEnabled = true
                    binding.btnSaveManual.isEnabled = true
                    return@launch
                }

                // 1. Upload photo to storage
                val photoUrl = storageRepository.uploadSpeedometerPhoto(userId, imageBytes)
                if (photoUrl == null) {
                    Toast.makeText(this@CameraActivity, "Failed to upload photo", Toast.LENGTH_SHORT).show()
                    binding.layoutProcessing.visibility = View.GONE
                    binding.btnConfirm.isEnabled = true
                    binding.btnSaveManual.isEnabled = true
                    return@launch
                }

                // 2. Save trip (auto updates daily_summary)
                val success = tripsRepository.saveTrip(userId, tripType, kmReading, photoUrl)
                if (success) {
                    Toast.makeText(this@CameraActivity, "Odometer logged successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@CameraActivity, "Failed to save trip logs", Toast.LENGTH_SHORT).show()
                    binding.layoutProcessing.visibility = View.GONE
                    binding.btnConfirm.isEnabled = true
                    binding.btnSaveManual.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.layoutProcessing.visibility = View.GONE
                binding.btnConfirm.isEnabled = true
                binding.btnSaveManual.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
