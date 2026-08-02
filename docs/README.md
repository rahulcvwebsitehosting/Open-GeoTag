# GeoTagImage (GTI) Documentation

GeoTagImage is an open-source Android SDK for GPS-based photo tagging, used in survey apps, property inspection, delivery tracking, and GIS systems.

---

## Why choose GTI?

|  |  |
|---|---|
| **Precision Geotagging** | Captures real-time Latitude, Longitude, and Altitude with high-accuracy GPS providers. |
| **Dynamic Map Snapshots** | Automatically fetches and embeds a Google Map preview of the current location onto the photo. |
| **Reverse Geocoding** | Converts coordinates into human-readable addresses (City, State, Country) automatically. |
| **CameraX Integration** | Built on Google's modern CameraX API for smooth hardware abstraction and stability. |
| **Design Customization** | Modify font family, text size, color, and background transparency to match your branding. |
| **Lightweight & Fast** | Optimized image processing ensures minimal lag between capture and saving. |
| **Smart Permissions** | Handles complex Android 13+ location and camera permission flows out of the box. |
| **Timestamping** | Embed localized date and time stamps in multiple formats directly into the image pixels. |
| **Flexible Output** | Choose between URI, File, or Bitmap outputs depending on your app's requirements. |
| **Kotlin & Java** | Full support for both languages with idiomatic extensions for Kotlin users. |

### Advanced Capabilities

|  |  |
|---|---|
| **Accuracy Control** | Select GPS priority, update intervals, and accuracy thresholds for enterprise-grade surveys. |
| **Offline Ready** | Location caching ensures image tagging works even without internet connectivity. |
| **Modular Design** | Use only what you need: camera, location, overlays, or metadata injection. |
| **Lifecycle Safe** | Automatically handles Activity & Fragment lifecycle changes without memory leaks. |
| **Scoped Storage** | Fully compatible with Android 10+ scoped storage and MediaStore guidelines. |
| **Test-Friendly** | Clean APIs allow mocking of location and camera for unit and UI testing. |

---

## Ideal Use Cases

- **Field Surveys** — Capture proof-based images for land, utility, and infrastructure surveys.
- **Property Inspection** — Attach verified location data to real-estate and housing inspections.
- **Compliance & Audits** — Ensure location authenticity for compliance, audits, and verification apps.
- **Delivery Proof** — Log timestamped, geotagged delivery images with zero manual effort.

---

## Setup

### 1. Add JitPack Repository

In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://jitpack.io")
    }
}
```

Or in `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```

### 2. Add Dependency

In your module-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.rahulcvwebsitehosting:Open-GeoTag:${latest_version}")
}
```

Or in `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.rahulcvwebsitehosting:Open-GeoTag:${latest_version}'
}
```

### 3. Add FileProvider in AndroidManifest.xml

Inside the `<application>` tag:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider_paths" />
</provider>
```

### 4. Create provider_paths.xml

In `res/xml/provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path name="external_files" path="."/>
</paths>
```

---

## Implementation

### Basic Setup

```kotlin
import com.dangiashish.GeoTagImage
import com.dangiashish.PermissionCallback

class MainActivity : AppCompatActivity(), PermissionCallback {
    private var gtiUri: Uri? = null
    private lateinit var geoTagImage: GeoTagImage
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... your layout setup
    }
}
```

### Register Launchers (in `onCreate` / `onCreateView`)

```kotlin
permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    val allGranted = permissions.all { it.value }
    if (allGranted) onPermissionGranted()
    else onPermissionDenied()
}

cameraLauncher =
    registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            gtiUri = geoTagImage.processCapturedImage()
            previewCapturedImage()
        } else {
            Toast.makeText(this, "Image capture failed", Toast.LENGTH_SHORT).show()
        }
    }
```

### Initialize GTI

```kotlin
geoTagImage = GeoTagImage(this, permissionLauncher, cameraLauncher)
geoTagImage.requestCameraAndLocationPermissions()

// Recommended beginner-friendly setup
geoTagImage.enableCameraX(true)
geoTagImage.enableSmartCapture(true)
geoTagImage.enableAutoStraighten(true)
geoTagImage.setImageStyle(GeoTagImage.ImageStyle.SMART_AUTO)

// Optional: override metadata manually
geoTagImage.setCustomLocation("Custom place", "Custom address", 28.6139, 77.2090)
geoTagImage.setCustomDateTime(customCalendar.timeInMillis)
// Call geoTagImage.clearCustomMetadata() to return to live GPS/current time.
```

### Open Camera

```kotlin
binding.ivCamera.setOnClickListener {
    geoTagImage.launchCamera(
        onImageCaptured = { uri ->
            if (uri != null) {
                gtiUri = uri
                previewCapturedImage()
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        },
        onFailure = {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    )
}
```

### Customize Geo Tags

```kotlin
geoTagImage.enableGTIService(false)  // Enable/Disable GTI features
geoTagImage.enableCameraX(true)      // Use CameraX as default
geoTagImage.setTextSize(30f)
geoTagImage.setBackgroundRadius(5f)
geoTagImage.setBackgroundColor(Color.parseColor("#66000000"))
geoTagImage.setTextColor(Color.WHITE)
geoTagImage.setAuthorName("Rahul")
geoTagImage.showAuthorName(true)
geoTagImage.showAppName(true)
geoTagImage.setImageExtension(PNG)
geoTagImage.setLabel("Clicked By")
geoTagImage.setDateFormat("yyyy-MM-dd HH:mm:ss")
geoTagImage.setCameraAspectRatio(RATIO_1X1)  // RATIO_1X1 | RATIO_4X3 | RATIO_16X9 | RATIO_FULL
geoTagImage.setMapView(MapViewType.SATELLITE) // ROADMAP | SATELLITE | TERRAIN | HYBRID
```

### Preview Captured Image

```kotlin
private fun previewCapturedImage() {
    gtiUri?.let { uri ->
        binding.ivImage.let { imageView ->
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}")
            }
        }
    }
}
```

### Permission Callbacks

```kotlin
override fun onPermissionGranted() {
    // Permissions granted — ready to capture
}

override fun onPermissionDenied() {
    geoTagImage.requestCameraAndLocationPermissions()
}
```

---

## Key Features Reference

| Feature | Method / Property |
|---|---|
| Enable/Disable GTI | `geoTagImage.enableGTIService(boolean)` |
| CameraX | `geoTagImage.enableCameraX(boolean)` |
| Smart Capture | `geoTagImage.enableSmartCapture(boolean)` |
| Auto Straighten | `geoTagImage.enableAutoStraighten(boolean)` |
| Image Style | `geoTagImage.setImageStyle(ImageStyle)` |
| Text Size | `geoTagImage.setTextSize(float)` |
| Background Radius | `geoTagImage.setBackgroundRadius(float)` |
| Background Color | `geoTagImage.setBackgroundColor(int)` |
| Text Color | `geoTagImage.setTextColor(int)` |
| Author Name | `geoTagImage.setAuthorName(String)` |
| Show Author | `geoTagImage.showAuthorName(boolean)` |
| Show App Name | `geoTagImage.showAppName(boolean)` |
| Image Format | `geoTagImage.setImageExtension(JPEG \| PNG)` |
| Custom Label | `geoTagImage.setLabel(String)` |
| Date Format | `geoTagImage.setDateFormat(String)` |
| Aspect Ratio | `geoTagImage.setCameraAspectRatio(RATIO_*)` |
| Map Type | `geoTagImage.setMapView(MapViewType)` |
| Custom Location | `geoTagImage.setCustomLocation(...)` |
| Custom Date/Time | `geoTagImage.setCustomDateTime(long)` |
| Clear Overrides | `geoTagImage.clearCustomMetadata()` |

---

## License

MIT License — Copyright (c) 2023–2026 Rahul S.

See the full [LICENSE](../LICENSE) file for details.
