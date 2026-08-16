# kqoif


~~Fast~~(Not really it is more of an exercise that grew a bit too much, it could be greately improved and if i have time / If I want I will make it better ), specification-compliant, modern Kotlin Multiplatform (KMP) library and CLI tool for the **QOI (Quite OK Image)** format.
---

## Features

- **Kotlin Multiplatform (KMP):** Pure `commonMain` logic ready for JVM, Android, WebAssembly (`wasmJs`, `wasmWasi`), JavaScript (`browser`, `nodejs`), and native desktop (Windows, Linux, macOS).
- **WebAssembly (Wasm):** Run in-browser and serverless QOI encoders and decoders via Wasm.
- **ImageIO Integration:** Convert between standard formats (PNG, JPG, BMP) and QOI seamlessly.
- **Automated CI/CD:** GitHub Actions test matrix across Windows, Linux, and macOS with automated tagged releases.

---

## Project Architecture

| Module | Target | Description |
| :--- | :--- | :--- |
| [`:kqoif-core`](file:///d:/Programming/kqoif/kqoif-core) | KMP (`commonMain`, JVM, Wasm, JS, Native) | Pure QOI header, chunks (`QoiOp*`), encoder, decoder, and diagnostics. |
| [`:kqoif-imageio`](file:///d:/Programming/kqoif/kqoif-imageio) | JVM | `BufferedImage` $\leftrightarrow$ `QoiImage` conversions and file I/O. |
| [`:kqoif-cli`](file:///d:/Programming/kqoif/kqoif-cli) | JVM / Native CLI | Command-line tool with `convert` and `dump` subcommands. |

---

## Library Quickstart

### 1. Dependency
```kotlin
dependencies {
    implementation("org.wip:kqoif-core:1.0.0")
    // Optional ImageIO integration for JVM:
    implementation("org.wip:kqoif-imageio:1.0.0")
}
```

### 2. Encoding & Decoding in Kotlin
```kotlin
import org.wip.kqoif.Color
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage

// 1. Create image
val header = QoiHeader(width = 800u, height = 600u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
val pixels = listOf(Color(255, 0, 0, 255), Color(0, 255, 0, 255) /* ... */)
val image = QoiImage(header, pixels)

// 2. Encode to QOI bytes
val qoiBytes: ByteArray = image.encode()

// 3. Decode from bytes
val decodedImage: QoiImage = QoiImage.decode(qoiBytes)
```

### 3. Converting PNG / JPG / BMP (JVM)
```kotlin
import org.wip.kqoif.imageio.QoiImageIO
import java.io.File

// Read PNG and write QOI
val qoiImage = QoiImageIO.read(File("input.png"))
QoiImageIO.write(qoiImage, File("output.qoi"))

// Convert QOI back to PNG
val decoded = QoiImageIO.read(File("output.qoi"))
QoiImageIO.write(decoded, File("restored.png"))
```

---

## CLI Usage

### Convert Images
```bash
# Convert PNG to QOI
kqoif convert input.png output.qoi

# Convert QOI to PNG with compression statistics
kqoif convert input.qoi output.png --stats
```

### Diagnostic & Pixel Dump
```bash
# Display QOI header and chunk distribution
kqoif dump input.qoi

# Dump first 50 pixels in formatted ASCII table
kqoif dump input.qoi --pixels --limit 50

# Dump pixel grid as JSON
kqoif dump input.qoi --pixels --format json --limit 100
```

---

## Building & Testing

```bash
# Build all modules and run full test suites across all targets
./gradlew check

# Run CLI locally
./gradlew :kqoif-cli:run --args="--help"
```

For release procedures and automated GitHub workflows, see [RELEASING.md](file:///d:/Programming/kqoif/RELEASING.md).
