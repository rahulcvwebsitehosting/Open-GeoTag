/*
 * MIT License
 *
 * Copyright (c) 2023-2026 Rahul S
 */
package com.geotagcv

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.dangiashish.GeoTagImage
import com.dangiashish.GeoTagImage.ImageStyle
import com.dangiashish.GeoTagImage.MapViewType
import com.dangiashish.GeoTagImage.Companion.JPEG
import com.dangiashish.GeoTagImage.Companion.PNG
import com.dangiashish.PermissionCallback
import com.geotagcv.databinding.ActivityMainBinding
import com.geotagcv.databinding.DialogSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), PermissionCallback {
    private var gtiUri: Uri? = null
    private lateinit var gti: GeoTagImage
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val customDateTime: Calendar = Calendar.getInstance()
    private var customDateTimeSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (cameraGranted && locationGranted) onPermissionGranted() else onPermissionDenied()
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                gtiUri = gti.processCapturedImage()
                previewCapturedImage()
            } else {
                showMessage("The photo was not captured")
            }
        }

        gti = GeoTagImage(this, permissionLauncher, cameraLauncher).apply {
            enableCameraX(true)
            enableSmartCapture(true)
            enableAutoStraighten(true)
            setImageStyle(ImageStyle.SMART_AUTO)
            setDateFormat("yyyy-MM-dd HH:mm:ss")
            setDirectory("Geo Tag Photo")
            showAuthorName(false)
            showAppName(false)
        }
        gti.requestCameraAndLocationPermissions()

        setupSettings()
        setupCaptureActions()
        setupStyleRecommendations()
        setupCustomMetadataEditor()
        setupAdvancedSettings()
        applyStyle(ImageStyle.SMART_AUTO)
    }

    private fun setupSettings() {
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun showSettingsDialog() {
        val settingsBinding = DialogSettingsBinding.inflate(layoutInflater)

        settingsBinding.btnPrivacy.setOnClickListener {
            showInformationDialog(R.string.privacy_policy, R.string.privacy_policy_body)
        }
        settingsBinding.btnTerms.setOnClickListener {
            showInformationDialog(R.string.terms_of_use, R.string.terms_of_use_body)
        }
        settingsBinding.btnGithubProfile.setOnClickListener {
            openExternalLink(R.string.credit_github_profile_url)
        }
        settingsBinding.btnProjectRepository.setOnClickListener {
            openExternalLink(R.string.credit_repository_url)
        }
        settingsBinding.btnProjectWebsite.setOnClickListener {
            openExternalLink(R.string.credit_website_url)
        }

        MaterialAlertDialogBuilder(this)
            .setView(settingsBinding.root)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun showInformationDialog(@StringRes title: Int, @StringRes message: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun openExternalLink(@StringRes url: Int) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(url)))
        runCatching { startActivity(intent) }
            .onFailure { showMessage(getString(R.string.no_browser_available)) }
    }

    private fun setupCaptureActions() {
        binding.btnCapture.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            binding.progressBar.visibility = View.VISIBLE
            gti.launchCamera(
                onImageCaptured = { uri ->
                    binding.progressBar.visibility = View.GONE
                    if (uri == null) {
                        showMessage("Could not capture the photo. Please try again.")
                    } else {
                        gtiUri = uri
                        previewCapturedImage()
                    }
                },
                onFailure = { message ->
                    binding.progressBar.visibility = View.GONE
                    showMessage(message ?: "Camera is unavailable")
                }
            )
            // The camera is modal; its capture control owns the processing state.
            binding.progressBar.visibility = View.GONE
        }

        binding.ivClose.setOnClickListener {
            binding.ivImage.setImageDrawable(null)
            binding.ivImage.visibility = View.GONE
            binding.ivClose.visibility = View.GONE
            binding.tvPhotoInsight.visibility = View.GONE
            binding.cardResult.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            gtiUri = null
        }
    }

    private fun setupStyleRecommendations() {
        binding.smartCaptureSwitch.setOnCheckedChangeListener { _, checked ->
            gti.enableSmartCapture(checked)
            binding.styleChips.isEnabled = checked
            for (index in 0 until binding.styleChips.childCount) {
                binding.styleChips.getChildAt(index).isEnabled = checked
            }
            if (checked) {
                val selectedStyle = styleForChip(binding.styleChips.checkedChipId)
                applyStyle(selectedStyle)
            } else {
                binding.tvRecommendationTitle.setText(R.string.manual_control_title)
                binding.tvRecommendationBody.setText(R.string.manual_control_body)
            }
        }

        binding.styleChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (binding.smartCaptureSwitch.isChecked) applyStyle(styleForChip(checkedId))
        }
    }

    private fun styleForChip(chipId: Int): ImageStyle = when (chipId) {
        R.id.chipLandscape -> ImageStyle.LANDSCAPE
        R.id.chipPortrait -> ImageStyle.PORTRAIT
        R.id.chipSquare -> ImageStyle.SQUARE
        R.id.chipFieldProof -> ImageStyle.FIELD_PROOF
        else -> ImageStyle.SMART_AUTO
    }

    private fun applyStyle(style: ImageStyle) {
        gti.setImageStyle(style)
        binding.sStraighten.isChecked = true

        when (style) {
            ImageStyle.SMART_AUTO -> {
                binding.tvRecommendationTitle.setText(R.string.recommendation_auto_title)
                binding.tvRecommendationBody.setText(R.string.recommendation_auto_body)
                setDetailDefaults(date = true, coordinates = true, map = true)
                binding.chipSatellite.isChecked = true
            }
            ImageStyle.LANDSCAPE -> {
                binding.tvRecommendationTitle.setText(R.string.recommendation_landscape_title)
                binding.tvRecommendationBody.setText(R.string.recommendation_landscape_body)
                setDetailDefaults(date = true, coordinates = true, map = true)
                binding.chipTerrain.isChecked = true
            }
            ImageStyle.PORTRAIT -> {
                binding.tvRecommendationTitle.setText(R.string.recommendation_portrait_title)
                binding.tvRecommendationBody.setText(R.string.recommendation_portrait_body)
                setDetailDefaults(date = true, coordinates = true, map = true)
                binding.chipSatellite.isChecked = true
            }
            ImageStyle.SQUARE -> {
                binding.tvRecommendationTitle.setText(R.string.recommendation_square_title)
                binding.tvRecommendationBody.setText(R.string.recommendation_square_body)
                setDetailDefaults(date = true, coordinates = false, map = false)
            }
            ImageStyle.FIELD_PROOF -> {
                binding.tvRecommendationTitle.setText(R.string.recommendation_field_title)
                binding.tvRecommendationBody.setText(R.string.recommendation_field_body)
                setDetailDefaults(date = true, coordinates = true, map = true)
                binding.chipHybrid.isChecked = true
            }
        }
        binding.buttonExtJpeg.isChecked = true
    }

    private fun setDetailDefaults(date: Boolean, coordinates: Boolean, map: Boolean) {
        binding.sDate.isChecked = date
        binding.sLatLng.isChecked = coordinates
        binding.sMap.isChecked = map
    }

    private fun setupAdvancedSettings() {
        binding.btnAdvanced.setOnClickListener {
            val expanding = binding.advancedContent.visibility != View.VISIBLE
            binding.advancedContent.visibility = if (expanding) View.VISIBLE else View.GONE
            binding.btnAdvanced.setText(
                if (expanding) R.string.advanced_expanded else R.string.advanced_collapsed
            )
        }

        binding.sStraighten.setOnCheckedChangeListener { _, checked ->
            gti.enableAutoStraighten(checked)
        }
        binding.sDate.setOnCheckedChangeListener { _, checked -> gti.showDate(checked) }
        binding.sLatLng.setOnCheckedChangeListener { _, checked -> gti.showLatLng(checked) }
        binding.sMap.setOnCheckedChangeListener { _, checked -> gti.showGoogleMap(checked) }
        binding.sAuthor.setOnCheckedChangeListener { _, checked ->
            gti.showAuthorName(checked)
            binding.authorInputLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.sApp.setOnCheckedChangeListener { _, checked ->
            gti.showAppName(checked)
            binding.appInputLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.etAuthorName.doAfterTextChanged { gti.setAuthorName(it?.toString()?.trim().orEmpty()) }
        binding.etAppName.doAfterTextChanged { gti.setAppName(it?.toString()?.trim().orEmpty()) }
        binding.etDirectoryName.doAfterTextChanged {
            val directory = it?.toString()?.trim().orEmpty()
            if (directory.isNotBlank()) gti.setDirectory(directory)
        }

        binding.toggleFormat.check(R.id.buttonExtJpeg)
        binding.toggleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            gti.setImageExtension(if (checkedId == R.id.buttonExtPng) PNG else JPEG)
        }

        binding.mapStyleChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val mapStyle = when (checkedIds.firstOrNull()) {
                R.id.chipHybrid -> MapViewType.HYBRID
                R.id.chipTerrain -> MapViewType.TERRAIN
                R.id.chipRoadmap -> MapViewType.ROADMAP
                else -> MapViewType.SATELLITE
            }
            gti.setMapView(mapStyle)
        }
    }

    private fun setupCustomMetadataEditor() {
        binding.customMetadataSwitch.setOnCheckedChangeListener { _, checked ->
            binding.customMetadataContent.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                gti.clearCustomMetadata()
                customDateTimeSelected = false
                customDateTime.timeInMillis = System.currentTimeMillis()
                binding.etCustomPlace.text?.clear()
                binding.etCustomAddress.text?.clear()
                binding.etCustomLatitude.text?.clear()
                binding.etCustomLongitude.text?.clear()
                binding.btnPickDate.setText(R.string.choose_date)
                binding.btnPickTime.setText(R.string.choose_time)
                clearCoordinateErrors()
            }
        }

        binding.etCustomPlace.doAfterTextChanged { applyCustomLocation() }
        binding.etCustomAddress.doAfterTextChanged { applyCustomLocation() }
        binding.etCustomLatitude.doAfterTextChanged { applyCustomLocation() }
        binding.etCustomLongitude.doAfterTextChanged { applyCustomLocation() }

        binding.btnPickDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    customDateTime.set(year, month, day)
                    customDateTimeSelected = true
                    applyCustomDateTime()
                },
                customDateTime.get(Calendar.YEAR),
                customDateTime.get(Calendar.MONTH),
                customDateTime.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    customDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    customDateTime.set(Calendar.MINUTE, minute)
                    customDateTime.set(Calendar.SECOND, 0)
                    customDateTimeSelected = true
                    applyCustomDateTime()
                },
                customDateTime.get(Calendar.HOUR_OF_DAY),
                customDateTime.get(Calendar.MINUTE),
                false
            ).show()
        }

        binding.btnResetMetadata.setOnClickListener {
            binding.customMetadataSwitch.isChecked = false
        }
    }

    private fun applyCustomLocation() {
        if (!binding.customMetadataSwitch.isChecked) return
        val latitudeText = binding.etCustomLatitude.text?.toString()?.trim().orEmpty()
        val longitudeText = binding.etCustomLongitude.text?.toString()?.trim().orEmpty()
        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()

        val coordinatePairComplete = latitudeText.isBlank() == longitudeText.isBlank()
        val latitudeValid = coordinatePairComplete &&
                (latitudeText.isBlank() || latitude?.let { it in -90.0..90.0 } == true)
        val longitudeValid = coordinatePairComplete &&
                (longitudeText.isBlank() || longitude?.let { it in -180.0..180.0 } == true)
        binding.latitudeInputLayout.error = when {
            !coordinatePairComplete -> getString(R.string.coordinate_pair_required)
            !latitudeValid -> getString(R.string.invalid_latitude)
            else -> null
        }
        binding.longitudeInputLayout.error = when {
            !coordinatePairComplete -> getString(R.string.coordinate_pair_required)
            !longitudeValid -> getString(R.string.invalid_longitude)
            else -> null
        }

        gti.setCustomLocation(
            place = binding.etCustomPlace.text?.toString(),
            address = binding.etCustomAddress.text?.toString(),
            latitude = if (latitudeValid) latitude else null,
            longitude = if (longitudeValid) longitude else null
        )
    }

    private fun applyCustomDateTime() {
        if (!customDateTimeSelected) return
        gti.setCustomDateTime(customDateTime.timeInMillis)
        binding.btnPickDate.text =
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(customDateTime.time)
        binding.btnPickTime.text =
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(customDateTime.time)
    }

    private fun clearCoordinateErrors() {
        binding.latitudeInputLayout.error = null
        binding.longitudeInputLayout.error = null
    }

    private fun previewCapturedImage() {
        val uri = gtiUri ?: return
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            binding.ivImage.setImageBitmap(bitmap)
            binding.ivImage.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.ivClose.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.tvPhotoInsight.text = buildPhotoInsight(bitmap)
            binding.tvPhotoInsight.visibility = View.VISIBLE
            binding.tvGTIPath.text = uri.path.orEmpty()
            binding.tvImgSize.text = getFileSize(uri.path)
            binding.cardResult.visibility = View.VISIBLE
        } catch (_: Exception) {
            binding.progressBar.visibility = View.GONE
            showMessage("The photo was saved, but its preview could not be loaded")
        }
    }

    private fun buildPhotoInsight(bitmap: Bitmap): String {
        val orientation = if (bitmap.width >= bitmap.height) "Landscape" else "Portrait"
        val ratio = maxOf(bitmap.width, bitmap.height).toFloat() / minOf(bitmap.width, bitmap.height)
        val ratioLabel = when {
            abs(ratio - 1f) < 0.08f -> "1:1"
            abs(ratio - 4f / 3f) < 0.08f -> "4:3"
            abs(ratio - 16f / 9f) < 0.10f -> "16:9"
            else -> "Full"
        }
        return getString(R.string.photo_insight, orientation, ratioLabel)
    }

    private fun getFileSize(filePath: String?): String {
        val file = filePath?.let(::File) ?: return ""
        if (!file.exists()) return ""
        val bytes = file.length()
        val formatter = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${formatter.format(bytes / 1024.0 / 1024.0)} MB"
            bytes >= 1024 -> "${formatter.format(bytes / 1024.0)} KB"
            else -> "$bytes bytes"
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionGranted() = Unit

    override fun onPermissionDenied() {
        showMessage("Camera and location permissions are needed for smart geotag photos")
    }

    override fun onDestroy() {
        if (::gti.isInitialized) gti.cleanup()
        super.onDestroy()
    }
}
