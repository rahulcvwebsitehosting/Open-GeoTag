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
- Tap-to-choose location (OpenStreetMap picker)
- Template previews on shuffled sample photos before applying a preset

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

## License

[MIT](LICENSE) — Copyright (c) 2023–2026 Rahul S
