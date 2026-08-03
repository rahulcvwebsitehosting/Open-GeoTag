<div align="center">

# GeoTagImage (GTI)

A lightweight, powerful Android library for capturing photos with embedded real-time metadata — location (Address, City, Lat/Lng), timestamps, author name, and a Google Maps preview, all overlaid directly onto the image.

[![JitPack](https://jitpack.io/v/rahulcvwebsitehosting/Open-GeoTag.svg)](https://jitpack.io/#rahulcvwebsitehosting/Open-GeoTag)
[![Android SDK 24+](https://img.shields.io/badge/android--sdk-24%2B-green)](https://developer.android.com/tools/sdkmanager)
[![Java](https://img.shields.io/badge/compatible-java-blue)](https://www.java.com/)
[![Kotlin](https://img.shields.io/badge/compatible-kotlin-blueviolet)](https://kotlinlang.org/)

**[Full documentation →](docs/)**

</div>

## Features

- CameraX camera with zoom, flip, and flash
- Google Maps overlay of the capture location
- Smart geocoding — address, city, and country
- Fully customizable text, colors, fonts, and transparency
- JPEG/PNG output, custom directories, EXIF GPS metadata
- Aspect ratios — 1:1, 4:3, 16:9, full-screen
- Smart capture, auto-straighten, style presets, geo-tag templates
- Photo library with recent capture history
- Tag pre-saved gallery/album photos with a chosen geo tag location
- Tap-to-choose location (OpenStreetMap picker)
- Template previews on shuffled sample photos before applying a preset

## Tag pre-saved photos

The Saved tab now exposes a **Tag a saved photo** card with two entry points:

- **Tag from gallery** — opens the Android photo picker so any image on the device can be tagged. The original file is never modified; a fresh tagged copy is written to the configured album (default `Geo Tag Photo`) and added to history.
- **Tag from saved photos** — picks from the existing in-app Saved list.

After selecting a photo, a review dialog shows the image preview, the resolved current location (place, address, lat/lng), editable Place and Address fields, and a **Pick a different location** button that opens the existing OpenStreetMap picker. The library connects the dots via the new public `GeoTagImage.applyGeoTagToImage(sourceUri, onProcessed)` API, which reuses the same overlay, EXIF, and album-save logic as the camera path.

```kotlin
gti.setCustomLocation(place, address, latitude, longitude)
gti.setCustomDateTime(System.currentTimeMillis())
gti.applyGeoTagToImage(sourceUri) { savedUri ->
    // savedUri points at the new tagged copy in your album
}
```

## Bug fixes

This release closes a detailed audit pass over the new tag-saved-photo flow and surrounding paths.

| # | Area | Bug | Fix |
|---|------|-----|-----|
| 1 | Library | `applyGeoTagToImage` could deadlock on the single-thread `executorService` when the user pre-supplied custom coordinates, because `loadMapForCurrentLocation` re-queued onto the same thread that `prepareAndRender` was already running on. | Run `prepareAndRender` directly; only `loadMapForCurrentLocation` schedules on `executorService`. |
| 2 | App | Tagging a saved photo mutated `GeoTagImage`'s internal custom-location/date state, which silently leaked into the next camera capture if the **Edit location and time** toggle was on. | Snapshot the user's camera custom-metadata UI state before tagging and restore it after `applyGeoTagToImage` returns, via `snapshotCameraCustomMetadata()` / `restoreCameraCustomMetadata()`. |
| 3 | App | The decoded review-dialog preview bitmap (up to 1600px side) was never recycled when the dialog was dismissed, causing a multi-megabyte leak across repeated uses. | Track the bitmap in `tagPhotoPreviewBitmap` and recycle it in the dialog's `setOnDismissListener`. |
| 4 | App | **Save tagged copy** could be tapped multiple times before processing finished, kicking off concurrent `applyGeoTagToImage` calls and duplicate album writes. | Disable the positive button while loading, re-enable it only when a valid location is resolved, and gate the save itself with a one-shot enable flag. |
| 5 | App | The "Tag from saved photos" chooser relied on `savedAdapter.currentList`, which is empty until the Saved tab has been visited at least once — the chooser would always show "no photos" on a fresh start. | Query `historyRepository.loadAllSavedPhotos()` directly before showing the chooser. |
| 6 | App | Opening a second tag-photo review while one was already in-flight overwrote the activity-level `tagPhotoDialog` reference, leaving stale listeners. | Guard both entry buttons against `tagPhotoDialog?.isShowing == true` and short-circuit `startTagPhotoReview` if the dialog is already open. |
| 7 | App | `binding.progressBar` lives on the **Camera** page, so showing it from the Saved page was a no-op — there was no visible progress feedback while tagging from the Saved tab. | Drive the dialog's own `ProgressBar` (`tagPhotoProgress`) and freeze inputs during save instead, then dismiss the dialog after the callback fires. |
| 8 | App | A stale asynchronous bitmap-callback could touch `DialogTagPhotoReviewBinding` after dismiss because the dismiss listener had already nulled fields but not the local binding. | Race-guarded the decode callback with `pendingTagPhoto?.sourceUri != sourceUri` (which already covered dismiss, since dismiss sets `pendingTagPhoto = null`) and additionally recycled the bitmap when the guard fired. |
| 9 | Strings | Three placeholder strings (`tag_photo_pick_location_helper`, `tag_photo_no_image_selected`, `tagged_copy_caption`) were declared but unreferenced; lint flagged them as `UnusedResources`. | Removed the unused strings. |
| 10 | Library | `return savedUri ?: return null` was a no-op `?:` after a `return`, written awkwardly and confusing to future readers. | Simplified to `return savedUri`. |

## Quick Start

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven(url = "https://jitpack.io") }
}

// build.gradle.kts
dependencies { implementation("com.github.rahulcvwebsitehosting:Open-GeoTag:<version>") }
```

Replace `<version>` with the latest release tag from the JitPack badge above. See **[docs/](docs/)** for the full setup guide and API reference.

## Credits

Built and maintained by **Rahul S** — Civil Engineer & Full-Stack AI Builder.

- GitHub: [rahulcvwebsitehosting](https://github.com/rahulcvwebsitehosting)
- LinkedIn: [rahulshyamcivil](https://www.linkedin.com/in/rahulshyamcivil/)
- X / Twitter: [@RahulShyamCv](https://x.com/RahulShyamCv)
- Instagram: [rahulcvjps](https://www.instagram.com/rahulcvjps/)
- Threads: [@RahulCvJPS](https://www.threads.net/@RahulCvJPS)
- WhatsApp: [Chat](https://wa.me/917305169964)
- Email: [rahulcvfiitjee@gmail.com](mailto:rahulcvfiitjee@gmail.com)

### Feature contributor

- **Tag-a-saved-photo feature & bug audit pass** — added the `applyGeoTagToImage` library API, the Saved-tab "Tag a saved photo" card, the tag-photo review dialog, the gallery picker (`PickVisualMedia`) and album-chooser flows, and resolved the ten bugs listed above. Patches live in this branch.

## License

[MIT](LICENSE) — Copyright (c) 2023–2026 Rahul S
