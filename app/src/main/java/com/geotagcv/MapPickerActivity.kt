package com.geotagcv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.geotagcv.databinding.ActivityMapPickerBinding
import java.util.Locale

class MapPickerActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMapPickerBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val initialLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, DEFAULT_LATITUDE)
        val initialLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, DEFAULT_LONGITUDE)

        binding.btnCancelMap.setOnClickListener { finish() }
        binding.btnUseMapLocation.setOnClickListener {
            binding.mapWebView.evaluateJavascript("window.GeoTagPicker.submit()", null)
        }

        configureMap(initialLatitude, initialLongitude)
    }

    @SuppressLint("SetJavaScriptEnabled") // Required only for the bundled Leaflet map; no JS bridge is exposed.
    private fun configureMap(latitude: Double, longitude: Double) {
        binding.mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            setSupportZoom(false)
        }
        binding.mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.tvMapStatus.setText(R.string.map_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.tvMapStatus.setText(R.string.map_picker_helper)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                return handlePickerUrl(request.url)
            }

            @Deprecated("Deprecated in Android WebView but required for older supported devices")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url?.let { handlePickerUrl(Uri.parse(it)) } ?: false
            }
        }

        val url = String.format(
            Locale.US,
            "file:///android_asset/map_picker.html?lat=%.7f&lng=%.7f",
            latitude,
            longitude
        )
        binding.mapWebView.loadUrl(url)
    }

    private fun handlePickerUrl(uri: Uri): Boolean {
        if (uri.scheme != PICKER_SCHEME || uri.host != PICKER_HOST) {
            // Keep navigations inside the bundled picker. Leaflet and map tiles load as subresources.
            return uri.scheme != "file"
        }
        val latitude = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return true
        val longitude = uri.getQueryParameter("lng")?.toDoubleOrNull() ?: return true
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return true

        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
        )
        finish()
        return true
    }

    override fun onDestroy() {
        binding.mapWebView.apply {
            stopLoading()
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LATITUDE = "map_picker_latitude"
        const val EXTRA_LONGITUDE = "map_picker_longitude"
        private const val PICKER_SCHEME = "geotagcv"
        private const val PICKER_HOST = "select"
        private const val DEFAULT_LATITUDE = 20.5937
        private const val DEFAULT_LONGITUDE = 78.9629
    }
}
