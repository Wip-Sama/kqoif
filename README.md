# kqoif


~~Fast~~(Not really it is more of an exercise that grew a bit too much, it could be greately improved and if i have time / If I want I will make it better ), specification-compliant, modern Kotlin Multiplatform (KMP) library and CLI tool for the **QOI (Quite OK Image)** format.
---

## Features

- **Kotlin Multiplatform (KMP):** Pure `commonMain` logic ready for JVM, Android, WebAssembly (`wasmJs`, `wasmWasi`), JavaScript (`browser`, `nodejs`), and native desktop (Windows, Linux, macOS).
- **Companion Object Interfaces:** Standardized `QoiOpCompanion` and `QoiChunkCompanion` contracts with chunk sizes, tag matching, and serialization methods.
- **Transitive Zero-Allocation Direct Encoding:** Direct `Color -> bytes` and primitive chunk writing without intermediate `QoiOp` and `ByteArray` allocations.
- **Rolling Streaming Processing:** `QoiRollingEncoder` and `QoiRollingDecoder` streaming pixel-by-pixel / row-by-row to slash memory footprint from $O(\text{width} \times \text{height})$ to $O(1)$ / $O(\text{width})$.
- **Extended ImageIO Support:** TwelveMonkeys plugins for comprehensive standard format support (PNG, JPEG, BMP, WebP, TIFF).
- **Built-in CLI Benchmarking:** `kqoif benchmark` tool comparing in-memory and rolling direct vs object strategies and verifying byte-for-byte exact identity.
- **Automated CI/CD:** GitHub Actions test matrix across Windows, Linux, and macOS with automated tagged releases.

---

## Project Architecture

| Module | Target | Description |
| :--- | :--- | :--- |
| [`:kqoif-core`](file:///d:/Programming/kqoif/kqoif-core) | KMP (`commonMain`, JVM, Wasm, JS, Native) | Pure QOI header, chunks (`QoiOp*`), companion interfaces, in-memory & rolling encoders/decoders, and diagnostics. |
| [`:kqoif-imageio`](file:///d:/Programming/kqoif/kqoif-imageio) | JVM | `BufferedImage` $\leftrightarrow$ `QoiImage` conversions, TwelveMonkeys ImageIO plugins, and scanline rolling streaming. |
| [`:kqoif-cli`](file:///d:/Programming/kqoif/kqoif-cli) | JVM / Native CLI | Command-line tool with `convert`, `dump`, and `benchmark` subcommands. |

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
import org.wip.kqoif.QoiEncoderStrategy
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage

// 1. Create image
val header = QoiHeader(width = 800u, height = 600u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
val pixels = listOf(Color(255, 0, 0, 255), Color(0, 255, 0, 255) /* ... */)
val image = QoiImage(header, pixels)

// 2. Transitive zero-allocation direct encoding (default)
val directBytes: ByteArray = image.encode(QoiEncoderStrategy.DIRECT)

// 3. Or AST object-based encoding
val objectBytes: ByteArray = image.encode(QoiEncoderStrategy.OBJECT)

// 4. Decode from bytes
val decodedImage: QoiImage = QoiImage.decode(directBytes)
```

### 3. Rolling / Streaming Mode (Minimal Memory Footprint)
```kotlin
import kotlinx.io.Buffer
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiRollingEncoder

val buffer = Buffer()
val encoder = QoiRollingEncoder(
    width = 1920u,
    height = 1080u,
    channels = QoiHeader.CHANNELS_RGBA,
    sink = buffer
)

// Stream pixels one by one or row by row
for (y in 0 until 1080) {
    for (x in 0 until 1920) {
        encoder.encodePixel(r = 255, g = 0, b = 0, a = 255)
    }
}
encoder.finish()
```

### 4. Converting PNG / JPG / BMP / WEBP / TIFF (JVM)
```kotlin
import org.wip.kqoif.imageio.QoiImageIO
import java.io.File

// Standard in-memory conversion
val qoiImage = QoiImageIO.read(File("input.png"))
QoiImageIO.write(qoiImage, File("output.qoi"))

// Rolling scanline conversion (O(1) memory)
QoiImageIO.convertRolling(File("input.png"), File("output.qoi"))
```

---

## CLI Usage

### Convert Images
```bash
# Convert with default transitive direct encoder
kqoif convert input.png output.qoi

# Convert using rolling streaming mode
kqoif convert input.png output.qoi --rolling

# Convert with AST object strategy and show statistics
kqoif convert input.png output.qoi --encoder object --stats
```

### Benchmark Encoder Strategies
```bash
# Compare In-Memory Direct, In-Memory Object, Rolling Direct, and Rolling Object
kqoif benchmark input.png

# Benchmark with custom iterations and JSON output
kqoif benchmark input.png -i 20 -w 5 --format json
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
# Run all tests and multiplatform checks via helper scripts
./test-all.ps1   # Windows PowerShell
./test-all.sh    # Linux / macOS / Bash

# Or via Gradle directly:
./gradlew testAll
./gradlew check

# Run CLI locally
./gradlew :kqoif-cli:run --args="--help"
```

For release procedures and automated GitHub workflows, see [RELEASING.md](file:///d:/Programming/kqoif/RELEASING.md).

