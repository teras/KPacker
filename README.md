# KPacker

**A Kotlin Multiplatform application packaging tool that creates native app bundles for multiple platforms**

KPacker simplifies the process of packaging Java applications with their dependencies and JRE distributions into platform-specific formats. Built with Kotlin Multiplatform, it provides a unified interface for creating distributable packages across Linux, macOS, and Windows.

## ✨ Features

- **Multi-Platform Support**: Package for Linux x64/ARM64, macOS x64, and Windows x64
- **JRE Bundling**: Automatically downloads and includes appropriate JRE distributions
- **Icon Processing**: Intelligent icon conversion and resizing for each platform
- **Native Formats**: Creates platform-specific packages:
  - Linux: AppImage files
  - macOS: .app bundles with DMG creation and code signing support
  - Windows: Executable installers with InnoSetup
  - Generic: JAR-based packages with launcher scripts for any platform
- **Container-Based Tools**: Uses Docker/Podman for reliable cross-platform builds
- **Code Signing**: Built-in support for macOS app signing and notarization
- **Template Support**: Custom DMG templates for macOS packaging

## 🚀 Quick Start

### Prerequisites

- Java 21 or higher
- Docker or Podman for container-based operations
- Git for version control

### Installation

1. Clone the repository:
```bash
git clone https://github.com/your-org/kpacker.git
cd kpacker
```

2. Build the project:
```bash
./gradlew fatJar
```

3. Run KPacker:
```bash
java -jar build/libs/kpacker-0.1.0-fat.jar --help
```

### Basic Usage

Package a Java application for Linux x64:
```bash
java -jar build/libs/kpacker-0.1.0-fat.jar \
  --source=/path/to/your/app/lib \
  --out=/path/to/output \
  --name=MyApp \
  --version=1.0.0 \
  --mainjar=myapp.jar \
  --target=LinuxX64
```

Package for macOS with custom icon:
```bash
java -jar build/libs/kpacker-0.1.0-fat.jar \
  --source=/path/to/your/app/lib \
  --out=/path/to/output \
  --name=MyApp \
  --version=1.0.0 \
  --mainjar=myapp.jar \
  --target=MacX64 \
  --icon=/path/to/icon.png
```

## 📋 Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `--source` | Directory containing your application JARs | ✅ |
| `--out` | Output directory for generated packages | ✅ |
| `--name` | Application name | ✅ |
| `--version` | Application version | ✅ |
| `--mainjar` | Main JAR file name | ✅ |
| `--target` | Target platform (LinuxX64, LinuxArm64, MacX64, WindowsX64, Generic) | ✅ |
| `--icon` | Path to application icon (PNG, SVG, PDF supported) | ❌ |
| `--enable-signing` | Enable code signing for macOS (requires certificates) | ❌ |
| `--p12-file` | Path to P12 certificate file for macOS signing | ❌ |
| `--p12-pass` | Path to file containing P12 password | ❌ |
| `--notary-json` | Path to notarization configuration JSON | ❌ |
| `--dmg-template` | Path to custom DMG template (ZIP or DMG) | ❌ |

## 🎯 Supported Targets

### LinuxX64 / LinuxArm64
- Creates AppImage files that run on most Linux distributions
- Includes desktop integration files
- Supports custom icons with automatic conversion

### MacX64
- Creates .app bundles following macOS conventions
- Generates DMG files for distribution
- Supports code signing and notarization
- Custom Info.plist generation
- Icon conversion to ICNS format

### WindowsX64
- Creates Windows executable installers using InnoSetup
- Embeds icons into executable files
- Generates uninstaller
- Registry integration for Add/Remove Programs

### Generic
- Creates JAR-based packages with launcher scripts
- Platform-agnostic packaging option that runs on any system with Java
- Includes batch (.bat) files for Windows and shell scripts for Unix-like systems
- Ideal for cross-platform distribution without platform-specific installers

## 🔧 Advanced Features

### Code Signing (macOS)

KPacker supports macOS code signing and notarization:

```bash
java -jar build/libs/kpacker-0.1.0-fat.jar \
  --source=/path/to/app \
  --target=MacX64 \
  --enable-signing=true \
  --p12-file=/path/to/certificate.p12 \
  --p12-pass=/path/to/password.txt \
  --notary-json=/path/to/notary-config.json
```

### Custom DMG Templates

Use custom DMG templates for branded macOS installers:

```bash
java -jar build/libs/kpacker-0.1.0-fat.jar \
  --target=MacX64 \
  --dmg-template=/path/to/template.zip
```

### Icon Processing

KPacker automatically processes icons for each platform:
- **Input formats**: PNG, SVG, PDF
- **Output**: Platform-specific formats (ICO for Windows, ICNS for macOS, PNG for Linux)
- **Automatic resizing**: Icons are standardized to appropriate sizes

## 🏗️ Development

### Building from Source

```bash
# Build JVM JAR (recommended for development)
./gradlew jvmJar

# Build fat JAR with all dependencies
./gradlew fatJar

# Build native executables
./gradlew build

# Run tests
./gradlew test
```

### Project Structure

```
src/
├── commonMain/kotlin/onl/ycode/kpacker/
│   ├── Application.kt              # Application model
│   ├── main.kt                     # CLI entry point
│   ├── packers/                    # Platform-specific configurators
│   │   ├── Configurator.kt
│   │   ├── LinuxBaseConfigurator.kt
│   │   ├── MacX64Configurator.kt
│   │   └── WindowsBaseConfigurator.kt
│   └── utils/                      # Utility classes
│       ├── ContainerRunner.kt      # Docker/Podman integration
│       ├── ImageConverter.kt       # Icon processing
│       ├── PackageDownloader.kt    # JRE distribution handling
│       └── FileUtils.kt           # File operations
```

### Architecture

KPacker uses a **Configurator Pattern** where each target platform has its own configurator that handles:
- JRE distribution fetching
- Directory structure creation
- Platform-specific post-processing
- Package format generation

The **Container Runner** system provides consistent build environments using Docker/Podman for operations requiring specific tools.

## 🐳 Container Dependencies

KPacker uses the `docker.io/teras/appimage-builder` container for various operations:
- Image conversion (ImageMagick, rsvg-convert)
- Linux AppImage creation
- Windows installer generation (InnoSetup)
- macOS DMG creation and signing tools

## 🤝 Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Development Guidelines

- Follow Kotlin coding conventions
- Use meaningful commit messages
- Update documentation for new features
- Ensure all tests pass

## 📄 License

This project is licensed under the BSD 3-Clause License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Uses [Okio](https://square.github.io/okio/) for file system operations
- JRE distributions from [Adoptium](https://adoptium.net/)
- Icon processing with ImageMagick and librsvg
- Windows installers with [Inno Setup](https://jrsoftware.org/isinfo.php)

---

**KPacker** - Simplifying cross-platform Java application packaging
