/*
 * MIT License
 *
 * Copyright (c) 2023-2026 Rahul S
 */
package com.geotagcv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
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
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.dangiashish.GeoTagImage
import com.dangiashish.GeoTagImage.ImageStyle
import com.dangiashish.GeoTagImage.MapViewType
import com.dangiashish.GeoTagImage.Companion.JPEG
import com.dangiashish.GeoTagImage.Companion.PNG
import com.dangiashish.PermissionCallback
import com.geotagcv.databinding.ActivityMainBinding
import com.geotagcv.databinding.DialogSettingsBinding
import com.geotagcv.databinding.ItemTemplatePreviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity(), PermissionCallback {
    private var gtiUri: Uri? = null
    private lateinit var gti: GeoTagImage
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var mapPickerLauncher: ActivityResultLauncher<Intent>
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val customDateTime: Calendar = Calendar.getInstance()
    private val historyRepository by lazy { PhotoHistoryRepository(applicationContext) }
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private lateinit var recentAdapter: PhotoHistoryAdapter
    private lateinit var savedAdapter: PhotoHistoryAdapter
    private var customDateTimeSelected = false
    private var updatingMetadataFields = false

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

        mapPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, Double.NaN)
            val longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, Double.NaN)
            if (!latitude.isNaN() && !longitude.isNaN()) {
                usePickedCoordinates(latitude, longitude)
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
        setupTemplates()
        setupPhotoHistory()
        setupBottomNavigation()
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
        settingsBinding.btnCreatorGithub.setOnClickListener {
            openExternalLink(R.string.credit_creator_github_url)
        }
        settingsBinding.btnLinkedin.setOnClickListener {
            openExternalLink(R.string.credit_linkedin_url)
        }
        settingsBinding.btnX.setOnClickListener {
            openExternalLink(R.string.credit_x_url)
        }
        settingsBinding.btnInstagram.setOnClickListener {
            openExternalLink(R.string.credit_instagram_url)
        }
        settingsBinding.btnThreads.setOnClickListener {
            openExternalLink(R.string.credit_threads_url)
        }
        settingsBinding.btnWhatsapp.setOnClickListener {
            openExternalLink(R.string.credit_whatsapp_url)
        }
        settingsBinding.btnEmail.setOnClickListener {
            openExternalLink(R.string.credit_email_url)
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
            if (checked) {
                prefillCurrentMetadata()
            } else {
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
                binding.tvCurrentLocation.setText(R.string.current_location_not_loaded)
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

        binding.btnRefreshLocation.setOnClickListener { prefillCurrentLocation() }
        binding.btnChooseLocation.setOnClickListener { openMapPicker() }

        binding.btnResetMetadata.setOnClickListener {
            binding.customMetadataSwitch.isChecked = false
        }
    }

    private fun applyCustomLocation() {
        if (!binding.customMetadataSwitch.isChecked || updatingMetadataFields) return
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

    private fun prefillCurrentMetadata() {
        customDateTime.timeInMillis = System.currentTimeMillis()
        customDateTimeSelected = true
        applyCustomDateTime()
        prefillCurrentLocation()
    }

    private fun prefillCurrentLocation() {
        if (!binding.customMetadataSwitch.isChecked) return
        binding.tvCurrentLocation.setText(R.string.current_location_loading)
        binding.btnRefreshLocation.isEnabled = false
        binding.btnChooseLocation.isEnabled = false
        gti.fetchCurrentLocationDetails { details ->
            if (isFinishing || isDestroyed) return@fetchCurrentLocationDetails
            binding.btnRefreshLocation.isEnabled = true
            binding.btnChooseLocation.isEnabled = true
            if (details == null) {
                binding.tvCurrentLocation.setText(R.string.current_location_unavailable)
                return@fetchCurrentLocationDetails
            }
            updatingMetadataFields = true
            binding.etCustomPlace.setText(details.place)
            binding.etCustomAddress.setText(details.address)
            binding.etCustomLatitude.setText(formatCoordinate(details.latitude))
            binding.etCustomLongitude.setText(formatCoordinate(details.longitude))
            updatingMetadataFields = false
            binding.tvCurrentLocation.text = getString(
                R.string.current_location_summary,
                details.place.ifBlank { getString(R.string.current_location) },
                details.latitude,
                details.longitude
            )
            applyCustomLocation()
        }
    }

    private fun openMapPicker() {
        val latitude = binding.etCustomLatitude.text?.toString()?.toDoubleOrNull()
        val longitude = binding.etCustomLongitude.text?.toString()?.toDoubleOrNull()
        val intent = Intent(this, MapPickerActivity::class.java).apply {
            if (latitude != null) putExtra(MapPickerActivity.EXTRA_LATITUDE, latitude)
            if (longitude != null) putExtra(MapPickerActivity.EXTRA_LONGITUDE, longitude)
        }
        mapPickerLauncher.launch(intent)
    }

    private fun usePickedCoordinates(latitude: Double, longitude: Double) {
        updatingMetadataFields = true
        binding.etCustomLatitude.setText(formatCoordinate(latitude))
        binding.etCustomLongitude.setText(formatCoordinate(longitude))
        updatingMetadataFields = false
        binding.tvCurrentLocation.text = getString(
            R.string.selected_location_summary,
            latitude,
            longitude
        )
        applyCustomLocation()
        resolvePickedAddress(latitude, longitude)
    }

    private fun resolvePickedAddress(latitude: Double, longitude: Double) {
        backgroundExecutor.execute {
            val result = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
            }.getOrNull()
            runOnUiThread {
                if (result == null || isFinishing || isDestroyed) return@runOnUiThread
                updatingMetadataFields = true
                binding.etCustomPlace.setText(
                    result.locality ?: result.subAdminArea ?: result.adminArea.orEmpty()
                )
                binding.etCustomAddress.setText(result.getAddressLine(0).orEmpty())
                updatingMetadataFields = false
                applyCustomLocation()
            }
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun clearCoordinateErrors() {
        binding.latitudeInputLayout.error = null
        binding.longitudeInputLayout.error = null
    }

    private fun setupTemplates() {
        val previewPhotos = listOf(
            R.drawable.template_preview_1,
            R.drawable.template_preview_2,
            R.drawable.template_preview_3,
            R.drawable.template_preview_4
        ).shuffled()

        configureTemplatePreview(
            templateBinding = binding.templateClassic,
            image = previewPhotos[0],
            name = R.string.template_classic,
            description = R.string.template_classic_description,
            previewTitle = R.string.template_preview_classic_title,
            previewMetadata = R.string.template_preview_classic_metadata
        ) {
            applyTemplate(ImageStyle.SMART_AUTO, R.id.chipSmartAuto, R.string.template_active_classic)
        }
        configureTemplatePreview(
            templateBinding = binding.templateTravel,
            image = previewPhotos[1],
            name = R.string.template_travel,
            description = R.string.template_travel_description,
            previewTitle = R.string.template_preview_travel_title,
            previewMetadata = R.string.template_preview_travel_metadata
        ) {
            applyTemplate(ImageStyle.LANDSCAPE, R.id.chipLandscape, R.string.template_active_travel)
        }
        configureTemplatePreview(
            templateBinding = binding.templateClean,
            image = previewPhotos[2],
            name = R.string.template_clean,
            description = R.string.template_clean_description,
            previewTitle = R.string.template_preview_clean_title,
            previewMetadata = R.string.template_preview_clean_metadata
        ) {
            applyTemplate(ImageStyle.SQUARE, R.id.chipSquare, R.string.template_active_clean)
        }
        configureTemplatePreview(
            templateBinding = binding.templateEvidence,
            image = previewPhotos[3],
            name = R.string.template_evidence,
            description = R.string.template_evidence_description,
            previewTitle = R.string.template_preview_evidence_title,
            previewMetadata = R.string.template_preview_evidence_metadata
        ) {
            applyTemplate(ImageStyle.FIELD_PROOF, R.id.chipFieldProof, R.string.template_active_evidence)
        }
    }

    private fun configureTemplatePreview(
        templateBinding: ItemTemplatePreviewBinding,
        @DrawableRes image: Int,
        @StringRes name: Int,
        @StringRes description: Int,
        @StringRes previewTitle: Int,
        @StringRes previewMetadata: Int,
        onSelected: () -> Unit
    ) {
        templateBinding.ivTemplatePhoto.setImageResource(image)
        templateBinding.tvTemplateName.setText(name)
        templateBinding.tvTemplateDescription.setText(description)
        templateBinding.tvPreviewTitle.setText(previewTitle)
        templateBinding.tvPreviewMetadata.setText(previewMetadata)
        templateBinding.root.contentDescription = getString(
            R.string.template_card_description,
            getString(name)
        )
        templateBinding.root.setOnClickListener { onSelected() }
    }

    private fun applyTemplate(style: ImageStyle, chipId: Int, @StringRes label: Int) {
        binding.smartCaptureSwitch.isChecked = true
        binding.styleChips.check(chipId)
        applyStyle(style)
        binding.tvActiveTemplate.setText(label)
        binding.bottomNavigation.selectedItemId = R.id.navCamera
        showMessage(getString(R.string.template_applied))
    }

    private fun setupPhotoHistory() {
        recentAdapter = PhotoHistoryAdapter(contentResolver, ::openSavedPhoto)
        savedAdapter = PhotoHistoryAdapter(contentResolver, ::openSavedPhoto)
        binding.recentList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recentAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
        binding.savedList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = savedAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.capturePage.visibility = if (item.itemId == R.id.navCamera) View.VISIBLE else View.GONE
            binding.templatesPage.visibility = if (item.itemId == R.id.navTemplates) View.VISIBLE else View.GONE
            binding.recentPage.visibility = if (item.itemId == R.id.navRecent) View.VISIBLE else View.GONE
            binding.savedPage.visibility = if (item.itemId == R.id.navSaved) View.VISIBLE else View.GONE
            when (item.itemId) {
                R.id.navRecent -> loadRecentHistory()
                R.id.navSaved -> loadSavedPhotos()
            }
            true
        }
        binding.bottomNavigation.selectedItemId = R.id.navCamera
    }

    private fun refreshVisibleHistory() {
        when (binding.bottomNavigation.selectedItemId) {
            R.id.navRecent -> loadRecentHistory()
            R.id.navSaved -> loadSavedPhotos()
        }
    }

    private fun loadRecentHistory() {
        backgroundExecutor.execute {
            val records = historyRepository.loadRecentPhotos()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                recentAdapter.submitList(records)
                binding.recentEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadSavedPhotos() {
        backgroundExecutor.execute {
            val records = historyRepository.loadAllSavedPhotos()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                savedAdapter.submitList(records)
                binding.savedEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openSavedPhoto(record: PhotoRecord) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(record.uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { showMessage(getString(R.string.no_gallery_available)) }
    }

    private fun previewCapturedImage() {
        val uri = gtiUri ?: return
        binding.progressBar.visibility = View.VISIBLE
        backgroundExecutor.execute {
            historyRepository.recordCapture(uri)
            val bitmap = decodePreview(uri)
            val size = getUriFileSize(uri)
            runOnUiThread {
                if (gtiUri != uri || isFinishing || isDestroyed) return@runOnUiThread
                binding.progressBar.visibility = View.GONE
                if (bitmap == null) {
                    showMessage("The photo was saved, but its preview could not be loaded")
                    return@runOnUiThread
                }
                binding.ivImage.setImageBitmap(bitmap)
                binding.ivImage.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                binding.ivClose.visibility = View.VISIBLE
                binding.tvPhotoInsight.text = buildPhotoInsight(bitmap)
                binding.tvPhotoInsight.visibility = View.VISIBLE
                binding.tvGTIPath.text = uri.toString()
                binding.tvImgSize.text = size
                binding.cardResult.visibility = View.VISIBLE
                refreshVisibleHistory()
            }
        }
    }

    private fun decodePreview(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val largestSide = maxOf(width, height)
                if (largestSide > PREVIEW_MAX_SIDE) {
                    val scale = PREVIEW_MAX_SIDE.toFloat() / largestSide
                    decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > PREVIEW_MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    private fun getUriFileSize(uri: Uri): String {
        val bytes = runCatching {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }.getOrNull() ?: 0L
        return when {
            bytes >= 1024L * 1024L -> "~ ${DecimalFormat("#.##").format(bytes / 1048576.0)} MB"
            bytes >= 1024L -> "~ ${DecimalFormat("#.##").format(bytes / 1024.0)} KB"
            bytes > 0L -> "~ $bytes Bytes"
            else -> ""
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

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionGranted() = Unit

    override fun onPermissionDenied() {
        showMessage("Camera and location permissions are needed for smart geotag photos")
    }

    override fun onDestroy() {
        if (::recentAdapter.isInitialized) recentAdapter.release()
        if (::savedAdapter.isInitialized) savedAdapter.release()
        backgroundExecutor.shutdownNow()
        if (::gti.isInitialized) gti.cleanup()
        super.onDestroy()
    }

    companion object {
        private const val PREVIEW_MAX_SIDE = 1600
    }
}
