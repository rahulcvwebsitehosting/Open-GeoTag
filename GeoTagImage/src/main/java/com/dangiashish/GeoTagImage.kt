/*
 * MIT License
 *
 * Copyright (c) 2023-2026 Rahul S
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.dangiashish

import android.Manifest
import android.R.attr.text
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.content.ContentResolver
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Window
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.dangiashish.GTIUtility.decodeSampledBitmap
import com.dangiashish.geotagimage.R
import com.dangiashish.geotagimage.databinding.CameraLayoutBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param context FragmentActivity or AppCompatActivity
 * @param permissionLauncher ActivityResultLauncher for camera and location permissions
 * @param cameraLauncher ActivityResultLauncher for camera capture (mandatory, if app/developer required using system camera)
 */
class GeoTagImage(
    private val context: FragmentActivity,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val cameraLauncher: ActivityResultLauncher<Uri>? = null
) {
    private var address: String = ""
    private var city: String = ""
    private var country: String = ""
    private var latLong = ""
    private var date = ""
    private var mapBitmap: Bitmap? = null
    private var imageExtension = ".jpg"
    private var fileUri: Uri? = null
    private var geocoder: Geocoder? = null
    private var latitude = 0.0
    private var longitude = 0.0
    private var customTextSize = 25f
    private var typeface = Typeface.DEFAULT
    private var radius = dpToPx(6f)
    private var backgroundColor = "#66000000".toColorInt()
    private var textColor = Color.WHITE
    private var backgroundHeight = 150f
    private var authorName: String = ""
    private var dateFormat: String = ""
    private var label: String = "Captured By"
    private var exifAppName: String? = ""
    private var showAuthorName = false
    private var showAppName = false
    private var showLatLng = true
    private var showDate = true
    private var showGoogleMap = true
    private val elementsList = ArrayList<String>()
    private var mapHeight = backgroundHeight.toInt()
    private var mapWidth = 140
    private var apiKey: String? = ""
    private var center: String? = ""
    private var dimension: String? = ""
    private var markerUrl: String? = ""
    private var mapView = MapViewType.SATELLITE
    private val executorService = Executors.newSingleThreadExecutor()
    private val TAG = "GTILogs"
    private var isEnabled = true
    private var currentPhotoPath: String? = null
    private var latestLocation: Location? = null
    private var directoryName: String? = "Camera"
    private var customPlace: String? = null
    private var customAddress: String? = null
    private var customLatitude: Double? = null
    private var customLongitude: Double? = null
    private var customDateTimeMillis: Long? = null

    // CameraX related variables
    private var useCameraX = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Camera UI components (managed internally)
    private var cameraDialog: Dialog? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var pendingCallback: ((Uri?) -> Unit)? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentZoomRatio = 1f
    private var cameraAspectRatio: Int = RATIO_AUTO
    private var capturedAspectRatio: Int = RATIO_4X3
    private var smartCaptureEnabled = true
    private var autoStraightenEnabled = true
    private var imageStyle = ImageStyle.SMART_AUTO
    private var deviceIsLandscape =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private var currentLevelAngle = 0f
    private var capturedLevelAngle = 0f
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var orientationListener: OrientationEventListener? = null
    private lateinit var binding: CameraLayoutBinding

    enum class FlashMode { ON, OFF, AUTO }

    enum class ImageStyle {
        SMART_AUTO,
        LANDSCAPE,
        PORTRAIT,
        SQUARE,
        FIELD_PROOF
    }

    data class SmartRecommendation(
        val style: ImageStyle,
        val title: String,
        val summary: String,
        val ratioLabel: String
    )

    /** A user-facing snapshot of the device location resolved by the library. */
    data class LocationDetails(
        val place: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )

    private var flashMode = FlashMode.OFF
    private var flashCode = 0

    private val levelSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!smartCaptureEnabled || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            if (sqrt(x * x + y * y) < 3f) return

            val absoluteAngle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
            val nearestRightAngle = round(absoluteAngle / 90f) * 90f
            var levelAngle = absoluteAngle - nearestRightAngle
            if (levelAngle > 45f) levelAngle -= 90f
            if (levelAngle < -45f) levelAngle += 90f

            currentLevelAngle = currentLevelAngle * 0.82f + levelAngle * 0.18f
            (context as Activity).runOnUiThread { updateLevelUi() }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    enum class MapViewType(val value: String) {
        ROADMAP("roadmap"),
        SATELLITE("satellite"),
        TERRAIN("terrain"),
        HYBRID("hybrid")
    }

    /**
     * Launch camera interface
     * If CameraX is enabled, shows custom camera UI
     * If CameraX is disabled, uses system camera (original behavior)
     * @param onImageCaptured callback with captured image URI
     */
    fun launchCamera(onImageCaptured: (Uri?) -> Unit, onFailure: (String?) -> Unit) {
        if (useCameraX) {
            val lifecycleOwner = getLifecycleOwner()
            if (lifecycleOwner != null) {
                showCameraXInterface(onImageCaptured)
            } else {
                Log.e(TAG, "Context must be FragmentActivity or AppCompatActivity for CameraX")
                onImageCaptured(null)
                onFailure.invoke("Context must be FragmentActivity or AppCompatActivity for CameraX")
            }
        } else {
            if (cameraLauncher == null) {
                onFailure.invoke("CameraLauncher is not initialized")
                return
            }
            pendingCallback = onImageCaptured
            preparePhotoUriAndLocation(onImageCaptured)
        }
    }

    private fun getLifecycleOwner(): LifecycleOwner? {
        return when (context) {
            else -> context
        }
    }

    private fun showCameraXInterface(onImageCaptured: (Uri?) -> Unit) {
        if (!checkCameraPermissions()) {
            requestCameraAndLocationPermissions()
            onImageCaptured(null)
            return
        }

        createCameraDialog(onImageCaptured)
        startCameraX()
    }

    private fun createCameraDialog(onImageCaptured: (Uri?) -> Unit) {
        var mediaPlayer: MediaPlayer? = null

        cameraDialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            binding = CameraLayoutBinding.inflate(layoutInflater)
            setContentView(binding.root)

            binding.zoomSeekBar.max = 90
            binding.zoomSeekBar.progress = 0
            binding.zoomValue.text = "1.0x"

            binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            updateSmartCameraUi()
            startSmartSensors(force = true)

            scaleGestureDetector = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return false

                        val minZoom = zoomState.minZoomRatio
                        val maxZoom = zoomState.maxZoomRatio

                        currentZoomRatio *= detector.scaleFactor
                        currentZoomRatio = currentZoomRatio.coerceIn(minZoom, maxZoom)
                        camera?.cameraControl?.setZoomRatio(currentZoomRatio)

                        val uiZoom = 1f + ((currentZoomRatio - minZoom) / (maxZoom - minZoom)) * 9f
                        binding.zoomSeekBar.progress = ((uiZoom - 1f) * 10).toInt()
                        binding.zoomValue.text = String.format(Locale.US, "%.1fx", uiZoom)

                        return true
                    }
                })

            binding.previewView.setOnTouchListener { v, event ->
                scaleGestureDetector?.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                true
            }

            binding.zoomSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val zoomState = camera?.cameraInfo?.zoomState?.value ?: return

                    val minZoom = zoomState.minZoomRatio
                    val maxZoom = zoomState.maxZoomRatio

                    // UI zoom 1x to 10x
                    val uiZoom = 1f + (progress / 10f)

                    val cameraZoom = minZoom + ((uiZoom - 1f) / 9f) * (maxZoom - minZoom)

                    camera?.cameraControl?.setZoomRatio(cameraZoom)
                    currentZoomRatio = cameraZoom

                    binding.zoomValue.text = String.format(Locale.US, "%.1fx", uiZoom)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            binding.btnCapture.setOnClickListener {
                it.isEnabled = false
                binding.tvSmartHint.text = "Getting location and preparing photo…"
                capturedAspectRatio = resolveCameraAspectRatio()
                capturedLevelAngle = currentLevelAngle
                if (mediaPlayer == null) mediaPlayer =
                    MediaPlayer.create(context, R.raw.sound_shutter)
                mediaPlayer?.start()

                it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()

                capturePhotoWithCameraX(onImageCaptured)
            }

            binding.btnClose.setOnClickListener { closeCameraDialog() }
            binding.btnFlip.setOnClickListener { flipCamera() }

            binding.ivFlash.setOnClickListener {
                if (flashCode >= 2) flashCode = 0 else flashCode += 1
                updateFlashUI()

            }

            setOnCancelListener {
                stopSmartSensors()
                stopCameraX()
                cameraDialog = null
            }
            setOnDismissListener { mediaPlayer?.release() }
            show()
        }
    }

    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Restart camera with new selector
        startCameraX()
    }

    private fun closeCameraDialog() {
        stopSmartSensors()
        cameraDialog?.dismiss()
        cameraDialog = null
        stopCameraX()
    }

    private fun startSmartSensors(force: Boolean = false) {
        if (!smartCaptureEnabled || (!force && cameraDialog?.isShowing != true)) return

        accelerometer?.let {
            sensorManager.registerListener(
                levelSensorListener,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val landscape = orientation in 45..134 || orientation in 225..314
                if (landscape != deviceIsLandscape) {
                    deviceIsLandscape = landscape
                    updateSmartCameraUi()
                    if (cameraAspectRatio == RATIO_AUTO && cameraProvider != null) {
                        startCameraX()
                    }
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun stopSmartSensors() {
        sensorManager.unregisterListener(levelSensorListener)
        orientationListener?.disable()
        orientationListener = null
    }

    private fun updateSmartCameraUi() {
        if (!::binding.isInitialized) return
        val recommendation = getSmartRecommendation()
        binding.tvSmartMode.text = if (smartCaptureEnabled) {
            "Smart • ${recommendation.title}"
        } else {
            "Manual • ${recommendation.ratioLabel}"
        }
        binding.tvSmartHint.text = recommendation.summary
        updateLevelUi()
    }

    private fun updateLevelUi() {
        if (!::binding.isInitialized || cameraDialog?.isShowing != true) return
        val tilt = abs(currentLevelAngle)
        binding.horizonLine.rotation = -currentLevelAngle
        binding.horizonLine.alpha = if (tilt < 1.5f) 1f else 0.72f
        binding.tvLevel.text = when {
            !autoStraightenEnabled -> "Alignment guide"
            tilt < 1.5f -> "Level"
            tilt <= MAX_AUTO_STRAIGHTEN_DEGREES -> "Auto-straighten ${String.format(Locale.US, "%.1f°", tilt)}"
            else -> "Rotate phone to level"
        }
        val color = if (tilt < 1.5f) Color.rgb(208, 188, 255) else Color.WHITE
        binding.horizonLine.setBackgroundColor(color)
        binding.tvLevel.setTextColor(color)
    }

    private fun resolveCameraAspectRatio(): Int {
        return if (cameraAspectRatio == RATIO_AUTO) {
            if (deviceIsLandscape) RATIO_16X9 else RATIO_4X3
        } else {
            cameraAspectRatio
        }
    }

    private fun startCameraX() {
        val lifecycleOwner = getLifecycleOwner() ?: return

        if (lifecycleOwner is Activity && (lifecycleOwner.isFinishing || lifecycleOwner.isDestroyed)) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                    bindCameraUseCases(lifecycleOwner)
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val screenMetrics = context.resources.displayMetrics
        val screenAspectRatio = aspectRatio(screenMetrics.widthPixels, screenMetrics.heightPixels)
        val resolvedRatio = resolveCameraAspectRatio()
        capturedAspectRatio = resolvedRatio

        // Adjust PreviewView Aspect Ratio
        binding.previewView.post {
            binding.previewView.let { pv ->
                val layoutParams = pv.layoutParams as ConstraintLayout.LayoutParams
                val landscape = deviceIsLandscape
                when (resolvedRatio) {
                    RATIO_4X3 -> {
                        layoutParams.dimensionRatio = if (landscape) "W,4:3" else "H,3:4"
                        layoutParams.width = 0
                        layoutParams.height = 0
                    }

                    RATIO_16X9 -> {
                        layoutParams.dimensionRatio = if (landscape) "W,16:9" else "H,9:16"
                        layoutParams.width = 0
                        layoutParams.height = 0
                    }

                    RATIO_FULL -> {
                        layoutParams.dimensionRatio = null
                        layoutParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                        layoutParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                    }

                    RATIO_1X1 -> {
                        layoutParams.dimensionRatio = "H,1:1"
                        layoutParams.width = 0
                        layoutParams.height = 0
                    }
                }
                pv.layoutParams = layoutParams
            }
        }

        // Preview
        val previewBuilder = Preview.Builder()

        // ImageCapture
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                when (flashMode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                }
            )

        if (resolvedRatio == RATIO_1X1) {
            val size = Size(1080, 1080)
            previewBuilder.setTargetResolution(size)
            imageCaptureBuilder.setTargetResolution(size)
        } else {
            val ratio = if (resolvedRatio == RATIO_FULL) screenAspectRatio else resolvedRatio
            previewBuilder.setTargetAspectRatio(ratio)
            imageCaptureBuilder.setTargetAspectRatio(ratio)
        }

        val preview = previewBuilder.build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }

        imageCapture = imageCaptureBuilder.build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageCapture
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /**
     * Update UI based on flash mode
     */
    private fun updateFlashUI() {
        when (flashCode) {
            0 -> {
                camera?.cameraControl?.enableTorch(false)
                flashMode = FlashMode.OFF
                binding.ivFlash.setImageResource(R.drawable.baseline_flash_off_24)
            }

            1 -> {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    camera?.cameraControl?.enableTorch(true)
                    flashMode = FlashMode.ON
                    binding.ivFlash.setImageResource(R.drawable.baseline_flash_on_24)
                }
            }

            2 -> {
                camera?.cameraControl?.enableTorch(false)
                flashMode = FlashMode.AUTO
                binding.ivFlash.setImageResource(R.drawable.baseline_flash_auto_24)
            }
        }
    }

    /**
     * Launch camera interface
     */
    private fun capturePhotoWithCameraX(onImageCaptured: (Uri?) -> Unit) {
        val imageCapture = imageCapture ?: run {
            onImageCaptured(null)
            return
        }

        // Get current location first
        fetchCurrentLocation {
            // Create output file
            val photoFile = createImageInternally()
            if (photoFile == null) {
                onImageCaptured(null)
                return@fetchCurrentLocation
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        (context as? Activity)?.runOnUiThread {
                            onImageCaptured(null)
                            closeCameraDialog()
                        }
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        currentPhotoPath = photoFile.absolutePath

                        // Process the captured image
                        val processedUri = processCapturedImage()

                        (context as? Activity)?.runOnUiThread {
                            onImageCaptured(processedUri)
                            closeCameraDialog()
                        }
                    }
                }
            )
        }
    }

    /**
     * Stop cameraX
     */
    private fun stopCameraX() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Check if camera permissions are granted
     */
    private fun checkCameraPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return cameraGranted && (fineLocationGranted || coarseLocationGranted)
    }

    /**
     * Request camera and location permissions
     */
    fun preparePhotoUriAndLocation(onReady: (Uri?) -> Unit) {
        if (useCameraX) {
            Log.w(TAG, "CameraX is enabled. Use launchCamera() instead.")
            onReady(null)
            return
        }

        if (checkCameraPermissions()) {
            fetchCurrentLocation {
                fileUri = createImageInternally()?.let {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
                }
                cameraLauncher?.launch(fileUri!!)
                onReady(fileUri)
            }
        } else {
            onReady(null)
            requestCameraAndLocationPermissions()
        }
    }

    /**
     * Fetch current location
     */
    private fun deviceLocation(
        location: Location,
        onResolved: () -> Unit,
        shouldLoadMap: Boolean = true
    ) {

        latitude = location.latitude
        longitude = location.longitude
        address = ""
        city = ""
        country = ""
        geocoder = Geocoder(context, Locale.getDefault())

        val completionDelivered = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val finishResolution: () -> Unit = {
            if (completionDelivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacksAndMessages(completionDelivered)
                if (shouldLoadMap) {
                    loadMapForCurrentLocation(onResolved)
                } else {
                    ContextCompat.getMainExecutor(context).execute(onResolved)
                }
            }
        }
        mainHandler.postAtTime(
            finishResolution,
            completionDelivered,
            android.os.SystemClock.uptimeMillis() + GEOCODING_TIMEOUT_MS
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder?.getFromLocation(latitude, longitude, 1) { addresses ->
                    addresses.firstOrNull()?.let(::processAddressResult)
                    finishResolution()
                } ?: finishResolution()
            } else {
                executorService.execute {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder?.getFromLocation(latitude, longitude, 1)
                            ?.firstOrNull()
                            ?.let(::processAddressResult)
                    } catch (e: Exception) {
                        Log.e(TAG, "Geocoding failed", e)
                    }
                    finishResolution()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            finishResolution()
        }
    }

    private fun loadMapForCurrentLocation(onComplete: () -> Unit) {
        if (!showGoogleMap || !GTIUtility.isGoogleMapsLinked(context)) {
            ContextCompat.getMainExecutor(context).execute(onComplete)
            return
        }

        apiKey = GTIUtility.getMapKey(context)
        val effectiveLatitude = customLatitude ?: latitude
        val effectiveLongitude = customLongitude ?: longitude
        center = "$effectiveLatitude,$effectiveLongitude"
        dimension = "${mapWidth.coerceAtLeast(DEFAULT_MAP_WIDTH)}x$mapHeight"
        markerUrl = "markers=color:red%7C$center&"
        val imageUrl =
            "https://maps.googleapis.com/maps/api/staticmap?center=$center&zoom=17&size=$dimension&$markerUrl" +
                    "maptype=${mapView.value}&key=$apiKey"

        executorService.execute {
            try {
                mapBitmap = loadImageFromUrl(imageUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Static map loading failed", e)
            }
            ContextCompat.getMainExecutor(context).execute(onComplete)
        }
    }

    /**
     * Process geocoded address
     */
    private fun processAddressResult(addressObj: Address) {
        address = addressObj.getAddressLine(0) ?: "Location: Not available"
        city = addressObj.locality ?: addressObj.adminArea ?: ""
        country = addressObj.countryName ?: ""
        country = GTIUtility.getCountryWithFlag(country, addressObj.countryCode ?: "")

    }

    /**
     * Scale image to max side
     */
    private fun processBitmapAspectRatio(bitmap: Bitmap): Bitmap {
        return when (capturedAspectRatio) {
            RATIO_1X1 -> centerCropToAspectRatio(bitmap, 1f)
            RATIO_4X3 -> centerCropToAspectRatio(bitmap, 4f / 3f)
            RATIO_16X9 -> centerCropToAspectRatio(bitmap, 16f / 9f)
            else -> bitmap
        }
    }

    private fun centerCropToAspectRatio(bitmap: Bitmap, longToShortRatio: Float): Bitmap {
        val targetRatio = if (bitmap.width >= bitmap.height) {
            longToShortRatio
        } else {
            1f / longToShortRatio
        }
        val currentRatio = bitmap.width.toFloat() / bitmap.height

        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > targetRatio) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetRatio).toInt()
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetRatio).toInt()
        }

        if (cropWidth == bitmap.width && cropHeight == bitmap.height) return bitmap
        val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    /**
     * Load image from URL
     */
    private fun loadImageFromUrl(imageUrl: String): Bitmap? {
        try {
            val connection = URL(imageUrl).openConnection().apply {
                connectTimeout = MAP_TIMEOUT_MS
                readTimeout = MAP_TIMEOUT_MS
            }
            connection.getInputStream().use { inputStream ->
                return BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Call this after receiving image capture result.
     */
    fun processCapturedImage(geoTagged: Boolean = isEnabled): Uri? {
        currentPhotoPath?.let { filePath ->
            val file = File(filePath)

            var bitmap = decodeSampledBitmap(filePath, 1280, 1280)

            val rotatedBitmap = setOrientation(filePath, bitmap)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = rotatedBitmap
            }

            val straightenedBitmap = autoStraightenBitmap(bitmap)
            if (straightenedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = straightenedBitmap
            }

            val croppedBitmap = processBitmapAspectRatio(bitmap)
            if (croppedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = croppedBitmap
            }

            if (useCameraX) {
                val scaled = scaleToMaxSide(bitmap, 1280)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            } else {
                bitmap = scaleToMaxSide(bitmap)
            }

            val finalBitmap = if (geoTagged) {
                val tagged = drawTextOnBitmap(bitmap)
                bitmap.recycle()
                tagged
            } else {
                bitmap
            }

            val savedUri = saveImageToGallery(finalBitmap)
            val outputStream = file.outputStream()
            finalBitmap.compress(outputCompressFormat(), outputQuality(), outputStream)
            outputStream.close()

            effectiveExifLocation()?.let { embedGeoTagInExif(filePath, it) }

            finalBitmap.recycle()

            // Prefer the gallery (MediaStore) Uri so the photo can be shared and opened
            // by other apps; fall back to the private file Uri only if the insert failed.
            return savedUri ?: Uri.fromFile(file)
        }
        return null
    }

    /**
     * Applies a geo tag overlay to an existing image selected by the user (for example,
     * a photo picked from the device gallery or the app's own Saved tab). The original
     * file is never modified — a new tagged copy is written to the configured album and
     * returned via [onProcessed]. Callers should pre-configure the tag using
     * [setCustomLocation], [setCustomDateTime], [showDate], [showLatLng], [showGoogleMap],
     * [setMapView], [setImageExtension], [setDirectory] and the other setters, exactly as
     * they would before a camera capture.
     *
     * When no custom coordinates have been supplied via [setCustomLocation] the library
     * attempts to resolve the device location first; if no fix is available, the overlay
     * is still drawn with whatever place/address the user supplied (or skipped if blank).
     *
     * @param sourceUri content Uri of the image to tag.
     * @param onProcessed callback receiving the Uri of the newly saved tagged copy,
     *        or null when the source image could not be decoded or saving failed.
     *        Always delivered on the main thread.
     */
    fun applyGeoTagToImage(sourceUri: Uri, onProcessed: (Uri?) -> Unit) {
        currentPhotoPath = null
        val hasUserCoordinates = customLatitude != null && customLongitude != null
        val prepareAndRender: () -> Unit = {
            loadMapForCurrentLocation {
                // Render, write and index the copy off the main thread; only the
                // result callback is delivered on the main thread.
                executorService.execute {
                    val savedUri = renderTaggedCopy(sourceUri)
                    ContextCompat.getMainExecutor(context).execute {
                        onProcessed(savedUri)
                    }
                }
            }
        }

        if (hasUserCoordinates) {
            prepareAndRender()
            return
        }

        fetchCurrentLocation(prepareAndRender)
    }

    private fun renderTaggedCopy(sourceUri: Uri): Uri? {
        val sourceBitmap = runCatching {
            val input = if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
                File(sourceUri.path ?: return null).inputStream()
            } else {
                contentResolverOrNull()?.openInputStream(sourceUri)
            }
            input?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull() ?: return null

        var bitmap = sourceBitmap
        val scaled = scaleToMaxSide(bitmap, 1280)
        if (scaled != bitmap) {
            bitmap.recycle()
            bitmap = scaled
        }

        val finalBitmap = if (isEnabled) {
            val tagged = drawTextOnBitmap(bitmap)
            bitmap.recycle()
            tagged
        } else {
            bitmap
        }

        val savedUri = saveImageToGallery(finalBitmap)

        if (savedUri != null) {
            effectiveExifLocation()?.let { location ->
                runCatching {
                    context.contentResolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        setExif(exif, location)
                    }
                }.onFailure { Log.e(TAG, "Error writing EXIF to tagged copy: ${it.localizedMessage}") }
            }
        }

        finalBitmap.recycle()
        return savedUri
    }

    private fun contentResolverOrNull() = (context as? Activity)?.contentResolver ?: context.contentResolver

    private fun autoStraightenBitmap(bitmap: Bitmap): Bitmap {
        if (!autoStraightenEnabled) return bitmap
        val correction = -capturedLevelAngle
        val angle = abs(correction)
        if (angle < 1f || angle > MAX_AUTO_STRAIGHTEN_DEGREES) return bitmap

        val matrix = Matrix().apply { postRotate(correction) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val crop = largestSafeCrop(bitmap.width, bitmap.height, angle)
        val cropWidth = crop.first.coerceIn(1, rotated.width)
        val cropHeight = crop.second.coerceIn(1, rotated.height)
        val left = ((rotated.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((rotated.height - cropHeight) / 2).coerceAtLeast(0)
        val result = Bitmap.createBitmap(rotated, left, top, cropWidth, cropHeight)
        if (result != rotated) rotated.recycle()
        return result
    }

    private fun largestSafeCrop(width: Int, height: Int, angleDegrees: Float): Pair<Int, Int> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val sinAngle = abs(sin(radians))
        val cosAngle = abs(cos(radians))
        val w = width.toDouble()
        val h = height.toDouble()
        val longSide = max(w, h)
        val shortSide = min(w, h)

        val cropWidth: Double
        val cropHeight: Double
        if (shortSide <= 2.0 * sinAngle * cosAngle * longSide) {
            val x = 0.5 * shortSide
            if (width >= height) {
                cropWidth = x / sinAngle
                cropHeight = x / cosAngle
            } else {
                cropWidth = x / cosAngle
                cropHeight = x / sinAngle
            }
        } else {
            val cosDouble = cosAngle * cosAngle - sinAngle * sinAngle
            cropWidth = (w * cosAngle - h * sinAngle) / cosDouble
            cropHeight = (h * cosAngle - w * sinAngle) / cosDouble
        }
        return cropWidth.toInt() to cropHeight.toInt()
    }

    /**
     * Scale image to max side
     */
    private fun scaleToMaxSide(bitmap: Bitmap, maxSide: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSide && height <= maxSide) return bitmap

        val ratio = if (width >= height) {
            maxSide.toFloat() / width
        } else {
            maxSide.toFloat() / height
        }

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    private fun outputCompressFormat(): Bitmap.CompressFormat {
        return if (imageExtension == PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    }

    private fun outputQuality(): Int = if (imageExtension == PNG) 100 else 90

    private fun effectiveCaptureDate(): Date = Date(customDateTimeMillis ?: System.currentTimeMillis())

    private fun effectiveExifLocation(): Location? {
        val manualLatitude = customLatitude
        val manualLongitude = customLongitude
        if (manualLatitude != null && manualLongitude != null) {
            return Location("manual").apply {
                latitude = manualLatitude
                longitude = manualLongitude
                time = customDateTimeMillis ?: System.currentTimeMillis()
            }
        }
        return latestLocation
    }

    /**
     * Set EXIF data for the captured image.
     */
    private fun embedGeoTagInExif(filePath: String, location: Location) {
        try {
            val exif = ExifInterface(filePath)

            val label = if (!exifAppName.isNullOrEmpty()) {
                ", Captured via $exifAppName"
            } else {
                ", Captured via GeoTagImage App"
            }
            exif.setAttribute(
                ExifInterface.TAG_DATETIME,
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    .format(effectiveCaptureDate())
            )
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL + label)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Android ${Build.VERSION.RELEASE}")

            exif.setGpsInfo(location)
            exif.saveAttributes()
        } catch (e: IOException) {
            Log.e(TAG, "Error writing EXIF data: ${e.localizedMessage}")
        }
    }

    /**
     * Set EXIF data for the captured image.
     */
    private fun setExif(exif: ExifInterface, location: Location) {
        val label = if (!exifAppName.isNullOrEmpty()) {
            ", Captured via $exifAppName"
        } else {
            ", Captured via GeoTagImage App"
        }
        exif.setAttribute(
            ExifInterface.TAG_DATETIME,
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                .format(effectiveCaptureDate())
        )
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL + label)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Android ${Build.VERSION.RELEASE}")

        exif.setGpsInfo(location)
        exif.saveAttributes()
    }

    /**
     * Save image to gallery
     */
    private fun saveImageToGallery(bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        var mimeType = "image/jpeg"
        when (imageExtension) {
            PNG -> mimeType = "image/png"
            JPEG -> mimeType = "image/jpeg"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timeStamp$imageExtension")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + "/" + directoryName
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(outputCompressFormat(), outputQuality(), out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            effectiveExifLocation()?.let { location ->
                uri?.let {
                    try {
                        context.contentResolver.openFileDescriptor(it, "rw")?.use { pfd ->
                            val exif = ExifInterface(pfd.fileDescriptor)
                            if (isEnabled) {
                                setExif(exif, location)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, ">Q Error writing EXIF via URI: ${e.localizedMessage}")
                    }
                }

            }
            uri
        } else {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    .toString() + "/" + directoryName
            val file = File(imagesDir, "IMG_$timeStamp$imageExtension")

            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(outputCompressFormat(), outputQuality(), out)
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            }
            effectiveExifLocation()?.let { location ->
                file.let {
                    try {
                        val exif = ExifInterface(file.absolutePath)
                        if (isEnabled) {
                            setExif(exif, location)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "<Q Error writing EXIF via URI: ${e.localizedMessage}")
                    }
                }
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    /**
     * Set the orientation of the image
     */
    private fun setOrientation(path: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Draw text on the image
     */
    private fun drawTextOnBitmap(bitmap: Bitmap): Bitmap {
        elementsList.clear()
        var formattedDate: String? = null
        if (showDate) {
            try {
                date = SimpleDateFormat(dateFormat, Locale.getDefault()).format(effectiveCaptureDate())
            } catch (e: Exception) {
                date = SimpleDateFormat("dd/MM/yyyy hh:mm a z", Locale.getDefault())
                    .format(effectiveCaptureDate())
            }
            formattedDate = date
        }
        val effectiveLatitude = customLatitude ?: latitude
        val effectiveLongitude = customLongitude ?: longitude
        val livePlace = listOf(city, country).filter(String::isNotBlank).joinToString(", ")
        elementsList.addAll(
            GTIMetadataFormatter.buildElements(
                place = customPlace ?: livePlace,
                address = customAddress ?: address,
                latitude = effectiveLatitude,
                longitude = effectiveLongitude,
                showCoordinates = showLatLng,
                dateText = formattedDate,
                authorText = if (showAuthorName) "$label $authorName" else null,
                appText = if (showAppName) "Captured via $exifAppName" else null
            )
        )

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        if (elementsList.isEmpty()) {
            return result
        }

        val textPaint = Paint().apply {
            color = textColor
            textSize = customTextSize
            isAntiAlias = true
            setShadowLayer(1f, 0f, 1f, Color.BLACK)
        }

        val bgPaint = Paint().apply {
            color = backgroundColor
        }

        val design = Paint()
        val padding = 20f
        val lineSpacing = 10f

        val effectiveMapWidth = if (mapBitmap != null && showGoogleMap) {
            mapWidth.coerceAtLeast(DEFAULT_MAP_WIDTH)
        } else {
            0
        }

        val maxTextWidth = result.width - effectiveMapWidth - 60

        mapBitmap?.let {
            if (effectiveMapWidth > 0 && mapHeight > 0) {
                val scaledMap = it.scale(effectiveMapWidth, mapHeight, false)
                canvas.drawBitmap(scaledMap, 10f, canvas.height - 160f, design)
            }
        }
        fun wrapText(line: String, paint: Paint, maxWidth: Int): List<String> {
            val words = line.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
            return lines
        }

        val allWrappedLines = mutableListOf<String>()
        for (text in elementsList) {
            text.split("\n").forEach { line ->
                allWrappedLines.addAll(wrapText(line, textPaint, maxTextWidth))
            }
        }

        val textHeight = textPaint.fontMetrics.run { bottom - top }
        val blockHeight = allWrappedLines.size * (textHeight + lineSpacing)
        val blockWidth = maxTextWidth + padding * 2

        val left = mapWidth + 20f
        val top = result.height - blockHeight - 30f
        val right = left + blockWidth - 10f
        val bottom = top + blockHeight + padding

        canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, bgPaint)

        var y = top + padding - textPaint.fontMetrics.top
        allWrappedLines.forEach { line ->
            canvas.drawText(line, left + padding, y, textPaint)
            y += textHeight + lineSpacing
        }

        return result
    }

    /**
     * Convert dp to px
     */
    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * Create a temporary image file
     * @return the file
     */
    private fun createImageInternally(): File? {
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return null

        val fileName = "IMG_$timeStamp$imageExtension"
        return File(storageDir, fileName).apply {
            currentPhotoPath = absolutePath
        }
    }

    /**
     * Get current location
     * @param onLocationReady the callback to be called when location is ready
     */
    private fun fetchCurrentLocation(onLocationReady: () -> Unit) {
        try {
            GTILocationUtility.fetchLocation(context) { location ->
                if (location != null) {
                    latestLocation = location
                    deviceLocation(location, onLocationReady)
                } else {
                    onLocationReady()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
            onLocationReady()
        }
    }

    /**
     * Set the text size
     * @param textSize the text size
     */
    fun setTextSize(textSize: Float) {
        this.customTextSize = textSize
    }

    /**
     * Set the custom font to be used
     * @param typeface the custom font to be used
     */
    fun setCustomFont(typeface: Typeface?) {
        this.typeface = typeface
    }

    /**
     * Set the background radius
     * @param radius the background radius
     */
    fun setBackgroundRadius(radius: Float) {
        this.radius = radius
    }

    /**
     * Set the background color
     * @param backgroundColor the background color
     */
    fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }

    /**
     * Set the background color
     * @param backgroundColor the background color
     */
    fun setBackgroundColor(backgroundColor: String) {
        this.backgroundColor = backgroundColor.toColorInt()
    }

    /**
     * Set the text color
     * @param textColor the text color
     */
    fun setTextColor(textColor: Int) {
        this.textColor = textColor
    }

    /**
     * Set the text color
     * @param textColor the text color
     */
    fun setTextColor(textColor: String) {
        this.textColor = textColor.toColorInt()
    }

    /**
     * Set whether to show the author name
     * @param showAuthorName true to show author name, false to hide author name
     */
    fun showAuthorName(showAuthorName: Boolean) {
        this.showAuthorName = showAuthorName
    }

    /**
     * Set whether to show the app name
     * @param showAppName true to show app name, false to hide app name
     */
    fun showAppName(showAppName: Boolean) {
        this.showAppName = showAppName
    }

    /**
     * Set whether to show the latitude and longitude
     * @param showLatLng true to show latitude and longitude, false to hide latitude and longitude
     */
    fun showLatLng(showLatLng: Boolean) {
        this.showLatLng = showLatLng
    }

    /**
     * Set whether to show the date
     * @param showDate true to show date, false to hide date
     */
    fun showDate(showDate: Boolean) {
        this.showDate = showDate
    }

    /**
     * Set Custom Date Format\
     */
    fun setDateFormat(dateFormat: String) {
        this.dateFormat = dateFormat
    }

    /**
     * Overrides the visible place/address and optionally the GPS coordinates for future captures.
     * Null or blank text falls back to the detected location. Coordinates are only overridden
     * when both values are valid.
     */
    fun setCustomLocation(
        place: String?,
        address: String?,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        customPlace = place?.trim()?.takeIf(String::isNotEmpty)
        customAddress = address?.trim()?.takeIf(String::isNotEmpty)
        val validCoordinates = latitude != null && longitude != null &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
        customLatitude = if (validCoordinates) latitude else null
        customLongitude = if (validCoordinates) longitude else null
    }

    /** Overrides capture date/time in the overlay and EXIF. Pass null to use current time. */
    fun setCustomDateTime(timestampMillis: Long?) {
        customDateTimeMillis = timestampMillis
    }

    /** Clears all user-entered place, coordinate, and date/time overrides. */
    fun clearCustomMetadata() {
        customPlace = null
        customAddress = null
        customLatitude = null
        customLongitude = null
        customDateTimeMillis = null
    }

    /**
     * Refreshes the device location and returns the same resolved values used on photos.
     * The callback is delivered on the main thread; null means that no location fix was available.
     */
    fun fetchCurrentLocationDetails(callback: (LocationDetails?) -> Unit) {
        try {
            GTILocationUtility.fetchLocation(context) { location ->
                if (location == null) {
                    ContextCompat.getMainExecutor(context).execute { callback(null) }
                    return@fetchLocation
                }
                latestLocation = location
                deviceLocation(location, shouldLoadMap = false, onResolved = {
                    val resolvedPlace = listOf(city, country)
                        .filter(String::isNotBlank)
                        .joinToString(", ")
                    callback(
                        LocationDetails(
                            place = resolvedPlace,
                            address = address,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                })
            }
        } catch (_: SecurityException) {
            ContextCompat.getMainExecutor(context).execute { callback(null) }
        }
    }

    /**
     * Set whether to show Google Maps in the image
     * @param showGoogleMap true to show Google Maps, false to hide Google Maps
     */
    fun showGoogleMap(showGoogleMap: Boolean) {
        this.showGoogleMap = showGoogleMap
    }

    /**
     * Set the author name to be displayed in the image
     * @param authorName the author name to be displayed
     */
    fun setAuthorName(authorName: String) {
        this.authorName = authorName
    }

    /**
     * Set the label to be displayed in the image
     * @param label the label to be displayed
     */
    fun setLabel(label: String) {
        this.label = label
    }

    /**
     * Set the app name to be displayed in the image
     * @param appName the app name to be displayed
     */
    fun setAppName(appName: String) {
        this.exifAppName = appName
    }

    /**
     * Enable or disable CameraX usage
     * @param useCameraX true to use CameraX, false to use system camera
     */
    fun enableCameraX(useCameraX: Boolean) {
        this.useCameraX = useCameraX
    }

    /**
     * Enables the beginner-friendly capture assistant. In Smart Auto it chooses 4:3 for
     * portrait captures and 16:9 for landscape captures, while keeping the level guide active.
     */
    fun enableSmartCapture(enabled: Boolean) {
        smartCaptureEnabled = enabled
        if (enabled && imageStyle == ImageStyle.SMART_AUTO) {
            cameraAspectRatio = RATIO_AUTO
            startSmartSensors()
        } else if (!enabled && cameraAspectRatio == RATIO_AUTO) {
            cameraAspectRatio = resolveCameraAspectRatio()
            stopSmartSensors()
        }
        updateSmartCameraUi()
    }

    /** Enable or disable the small-angle horizon correction applied after capture. */
    fun enableAutoStraighten(enabled: Boolean) {
        autoStraightenEnabled = enabled
        updateLevelUi()
    }

    /**
     * Applies a complete recommended capture preset. Callers can still override individual
     * settings after applying a style.
     */
    fun setImageStyle(style: ImageStyle) {
        imageStyle = style
        smartCaptureEnabled = true
        autoStraightenEnabled = true
        imageExtension = JPEG

        when (style) {
            ImageStyle.SMART_AUTO -> {
                cameraAspectRatio = RATIO_AUTO
                showDate = true
                showLatLng = true
                showGoogleMap = true
                mapView = MapViewType.SATELLITE
            }

            ImageStyle.LANDSCAPE -> {
                cameraAspectRatio = RATIO_16X9
                showDate = true
                showLatLng = true
                showGoogleMap = true
                mapView = MapViewType.TERRAIN
            }

            ImageStyle.PORTRAIT -> {
                cameraAspectRatio = RATIO_4X3
                showDate = true
                showLatLng = true
                showGoogleMap = true
                mapView = MapViewType.SATELLITE
            }

            ImageStyle.SQUARE -> {
                cameraAspectRatio = RATIO_1X1
                showDate = true
                showLatLng = false
                showGoogleMap = false
            }

            ImageStyle.FIELD_PROOF -> {
                cameraAspectRatio = RATIO_4X3
                showDate = true
                showLatLng = true
                showGoogleMap = true
                mapView = MapViewType.HYBRID
            }
        }

        updateSmartCameraUi()
        if (cameraProvider != null && cameraDialog?.isShowing == true) startCameraX()
    }

    fun getSmartRecommendation(): SmartRecommendation {
        val resolvedStyle = when {
            imageStyle != ImageStyle.SMART_AUTO -> imageStyle
            deviceIsLandscape -> ImageStyle.LANDSCAPE
            else -> ImageStyle.PORTRAIT
        }
        return when (resolvedStyle) {
            ImageStyle.SMART_AUTO -> SmartRecommendation(
                resolvedStyle,
                "Smart Auto",
                "Adapts framing and alignment for this photo",
                "Auto"
            )
            ImageStyle.LANDSCAPE -> SmartRecommendation(
                resolvedStyle,
                "Landscape 16:9",
                "Wide framing selected • horizon correction on",
                "16:9"
            )
            ImageStyle.PORTRAIT -> SmartRecommendation(
                resolvedStyle,
                "Portrait 4:3",
                "Natural portrait framing • alignment correction on",
                "4:3"
            )
            ImageStyle.SQUARE -> SmartRecommendation(
                resolvedStyle,
                "Square 1:1",
                "Centered crop • clean social-ready overlay",
                "1:1"
            )
            ImageStyle.FIELD_PROOF -> SmartRecommendation(
                resolvedStyle,
                "Field proof 4:3",
                "Date, coordinates and hybrid map preserved",
                "4:3"
            )
        }
    }

    /**
     * Set the aspect ratio for the camera preview and captured image
     * @param ratio Ratio_1X1, Ratio_4X3, Ratio_16X9, Ratio_Full
     */
    fun setCameraAspectRatio(ratio: Int) {
        this.cameraAspectRatio = when (ratio) {
            RATIO_AUTO, RATIO_1X1, RATIO_4X3, RATIO_16X9, RATIO_FULL -> ratio
            else -> RATIO_AUTO
        }
        if (cameraProvider != null && cameraDialog?.isShowing == true) {
            startCameraX()
        }
    }

    /**
     * Set the image extension
     * @param imgExtension the image extension
     */
    fun setImageExtension(imgExtension: String) {
        when (imgExtension) {
            PNG -> imageExtension = ".png"
            JPEG -> imageExtension = ".jpg"
        }
    }

    /**
     * Enable or disable GeoTagService
     * @param isActive true to enable GeoTagService, false to disable GeoTagService
     */
    fun enableGTIService(isActive: Boolean) {
        this.isEnabled = isActive
    }

    /**
     * Clean up resources when done
     * Call this in onDestroy() of your Activity
     */
    fun cleanup() {
        try {
            stopSmartSensors()
            cameraDialog?.dismiss()
            cameraDialog = null

            executorService.shutdownNow()
            cameraExecutor.shutdownNow()

            cameraProvider?.unbindAll()

            mapBitmap?.recycle()
            mapBitmap = null
            latestLocation = null
            pendingCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Set MapView Style
     */
    fun setMapView(mapView: MapViewType) {
        this.mapView = mapView
    }

    /**
     * Set Directory Name
     */
    fun setDirectory(directoryName: String) {
        this.directoryName = directoryName
        this.directoryName?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    companion object {
        const val PNG = ".png"
        const val JPEG = ".jpg"

        const val RATIO_1X1 = 3
        const val RATIO_4X3 = 0
        const val RATIO_16X9 = 1
        const val RATIO_FULL = 2
        const val RATIO_AUTO = 4
        private const val MAX_AUTO_STRAIGHTEN_DEGREES = 12f
        private const val DEFAULT_MAP_WIDTH = 140
        private const val MAP_TIMEOUT_MS = 4_000
        private const val GEOCODING_TIMEOUT_MS = 5_000L
    }

    /**
     * Request camera and location permissions
     */
    fun requestCameraAndLocationPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9 or below
            val permissionsToRequest = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
            return
        }
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    @Deprecated("getImageUri() is now deprecated, please use local {{uri?.path}}")
    fun getImagePath(): String {
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "/"
        )
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return ""
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val mImageName = "IMG_$timeStamp$imageExtension"
        val imagePath = mediaStorageDir.path + File.separator + mImageName
        val media = File(imagePath)
        MediaScannerConnection.scanFile(context, arrayOf(media.absolutePath), null) { _, _ -> }
        return imagePath
    }

    @Deprecated("getImageUri() is now deprecated, please use local url preparePhotoUriAndLocation()")
    fun getImageUri(): Uri? {

        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "/"
        )
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val mImageName = "IMG_$timeStamp$imageExtension"
        val imagePath = mediaStorageDir.path + File.separator + mImageName
        val media = File(imagePath)
        return Uri.fromFile(media)
    }
}
