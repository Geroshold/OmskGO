package com.example.go

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import com.google.gson.Gson

@OptIn(ExperimentalGetImage::class)
class ScannerFragment : Fragment() {

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var scanButton: Button

    private lateinit var barcodeScanner: BarcodeScanner
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scanner, container, false)

        // Инициализируем views через findViewById
        previewView = view.findViewById(R.id.preview_view)
        resultTextView = view.findViewById(R.id.result_text)
        scanButton = view.findViewById(R.id.scan_button)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBarcodeScanner()
        setupClickListeners()
    }

    private fun setupBarcodeScanner() {
        barcodeScanner = BarcodeScanning.getClient()
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            if (hasCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Для сканирования QR-кодов нужен доступ к камере", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image analysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                resultTextView.text = "Наведите камеру на QR-код"

            } catch(exc: Exception) {
                Log.e("ScannerFragment", "Use case binding failed", exc)
                Toast.makeText(requireContext(), "Ошибка запуска камеры", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            activity?.runOnUiThread {
                                processScannedCode(rawValue)
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("ScannerFragment", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    // Всегда закрываем imageProxy когда закончили
                    imageProxy.close()
                }
        } else {
            // Если изображение null, все равно закрываем proxy
            imageProxy.close()
        }
    }

    private fun processScannedCode(scannedText: String) {
        try {
            // Парсим JSON из QR-кода
            val gson = Gson()
            val qrData = gson.fromJson(scannedText, QrData::class.java)

            // Форматируем результат
            val resultText = """
                📍 Объект: ${qrData.objectName}
                🏆 Начислено баллов: ${qrData.points}
                📅 Время посещения: ${qrData.timestamp}
                
                ${qrData.description}
            """.trimIndent()

            resultTextView.text = resultText

            // Здесь можно добавить логику для сохранения баллов пользователю
            savePointsToProfile(qrData.points)

        } catch (e: Exception) {
            // Если не JSON, показываем простой текст
            resultTextView.text = "Отсканирован код: $scannedText\n\nЭто не распознанный QR-код объекта"
        }
    }

    private fun savePointsToProfile(points: Int) {
        // Здесь реализуйте логику сохранения баллов в профиль пользователя
        Toast.makeText(requireContext(), "Начислено $points баллов!", Toast.LENGTH_SHORT).show()

        // Пример сохранения в SharedPreferences:
        val sharedPref = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        val currentPoints = sharedPref.getInt("user_points", 0)
        sharedPref.edit().putInt("user_points", currentPoints + points).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}

// Data class для хранения информации из QR-кода
data class QrData(
    val objectName: String,
    val points: Int,
    val timestamp: String,
    val description: String = ""
)