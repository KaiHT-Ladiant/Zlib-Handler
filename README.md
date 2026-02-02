<img width="1212" height="870" alt="image" src="https://github.com/user-attachments/assets/815b7fc5-c18a-45fb-a462-6976a54369d0" /># Zlib Handler - Burp Suite Extension

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Burp Suite](https://img.shields.io/badge/Burp%20Suite-Extension-orange.svg)](https://portswigger.net/burp)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![Platform](https://img.shields.io/badge/Platform-Cross--Platform-lightgrey.svg)](https://portswigger.net/burp)

A Burp Suite extension for compressing and decompressing Zlib-encoded HTTP response bodies.

## Features

- **Zlib Compression/Decompression**: Decompress Zlib-compressed data from HEX format and compress plain text back to Zlib
- **History Integration**: Automatically detect and display Zlib-compressed responses from Burp Suite's Proxy History
- **Auto Decompression**: Click on history items to automatically extract HEX data and decompress
- **Encoding Support**: Auto-detect and manually select from various encodings (UTF-8, EUC-KR, CP949, etc.)
- **JSON Pretty Print**: Automatically format JSON data for better readability
- **Scope Filtering**: Filter and display only URLs within Burp Suite's scope
- **Intercept Feature**: Automatically decompress Zlib-compressed responses in Proxy Intercept
- **Performance Optimized**: Fast processing of large data and efficient history updates

## Screenshots

![Zlib Handler Extension](<img width="1212" height="870" alt="image" src="https://github.com/user-attachments/assets/989f6a5b-511f-4882-84d4-e9cf62dba7f0" />
)

## Requirements

- Burp Suite Professional or Community Edition
- Java 17 or higher (for Java Extension)
- Maven 3.6 or higher (for building)

## Installation

### 1. Download from Releases (Recommended)

1. Download the latest JAR file from the [Releases](https://github.com/yourusername/zlib-handler/releases) page.
2. Launch Burp Suite.
3. Navigate to the **Extender** tab.
4. Click **Add** in the **Extensions** section.
5. Select **Extension type** as **Java**.
6. Select the downloaded JAR file.
7. Click **Next** to load the extension.

### 2. Build from Source

#### Requirements
- Java 17 or higher
- Maven 3.6 or higher
- Burp Suite Professional JAR (for compilation)

#### Build Commands

```bash
# Windows
.\build-java.ps1

# Linux/Mac
mvn clean package
```

After building, the `target/zlib-handler-1.0.0.jar` file will be created.

**Note**: The `build-java.ps1` script automatically finds the Burp Suite JAR and installs it to the local Maven repository. Make sure you have Maven installed and in your PATH.

**Note**: The `build-java.ps1` script automatically finds the Burp Suite JAR and installs it to the local Maven repository.

## Usage

### 1. Direct HEX Data Decompression

1. Open the **Zlib Handler** tab in Burp Suite.
2. Paste HEX-format Zlib-compressed data into the **HEX Input** area.
   - Spaces, line breaks, colons (:), hyphens (-) are automatically removed.
   - Example: `FF AD 78 01 B5 94 4F 68...` or `FFAD7801B5944F68...`
3. Select an encoding from the **Encoding** dropdown or choose "Auto-detect".
4. Check the **Pretty Print JSON** checkbox to automatically format JSON.
5. Click the **Decompress Zlib** button.
6. The decompressed result will be displayed in the **Decompressed Output** area.

### 2. Edit Plain Text and Compress

1. Edit the plain text directly in the **Decompressed Output** area.
2. Click the **Compress to Zlib** button.
3. The compressed HEX data will be displayed in the **HEX Input** area.

### 3. Decompress from History

1. In the **Zlib Handler** tab, check **Show only in-scope URLs** to display only URLs within scope.
2. Click the **Refresh History** button to refresh the history.
3. Click on an item in the history table:
   - HEX data will be automatically loaded into the **HEX Input** area.
   - If Zlib-compressed, it will be automatically decompressed and displayed in **Decompressed Output**.
4. Click table headers to sort (the # column is sorted in ascending order by default).

### 4. Intercept Feature

1. Check the **Intercept and auto-decompress Zlib responses** checkbox.
2. Enable Intercept in Burp Suite's **Proxy** > **Intercept** tab.
3. Zlib-compressed responses will be automatically decompressed and displayed.

## Zlib Header Detection

The following Zlib headers are automatically detected:
- `0x78 0x9C` (default compression)
- `0x78 0x01` (minimum compression)
- `0x78 0xDA` (maximum compression)
- `0x78 0x5E` (other)
- `0x78 0x9D`, `0x78 0xBB`, etc.

**Note**: Even with prefixes like `FF AD 78 01`, the `78 01` header is automatically found and processed.

## Supported Encodings

- **Auto-detect**: Automatically selects the optimal encoding (prioritizes Korean characters)
- UTF-8
- EUC-KR
- CP949 / Windows-949
- ISO-8859-1
- US-ASCII

## Troubleshooting

### "Error: Hex string must have even length"
- The HEX string length is not even. Check for spaces or special characters.

### "Error decompressing: incorrect header check"
- The data may not be in Zlib format or may be corrupted.
- Check the beginning of the HTTP response body.
- Prefixes like `FF AD` are automatically handled, but if the problem persists, manually verify the Zlib header position.

### History is empty
- Click the **Refresh History** button to refresh the history.
- Ensure Burp Suite's Proxy is enabled and requests are being captured.

### Korean characters are garbled
- Try selecting "EUC-KR" or "CP949" from the **Encoding** dropdown.
- "Auto-detect" automatically detects Korean characters, but you can manually select a specific encoding if needed.

### Performance issues
- For large histories, use the **Show only in-scope URLs** filter to limit data.
- History is automatically sorted every 10 items, so there may be slight delays during bulk updates.

## Development

### Project Structure

```
.
├── src/
│   └── main/
│       └── java/
│           └── burp/
│               └── ZlibDecompressor.java
├── lib/
│   └── burpsuite_pro.jar (for compilation, not included in Git)
├── pom.xml
├── build-java.ps1
└── README.md
```

### Build Script

The `build-java.ps1` script performs the following:
1. Finds and copies the Burp Suite JAR to the `lib/` directory
2. Installs it to the local Maven repository
3. Builds the project with Maven

## Contributing

Bug reports, feature suggestions, and Pull Requests are welcome!

1. Fork this repository.
2. Create a feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Disclaimer

This tool should only be used for security testing and authorized penetration testing purposes. Users are responsible for any unauthorized use.

## Acknowledgments

- PortSwigger Web Security's Burp Suite team
- All contributors

## Changelog

### v1.0.0
- Initial release
- Zlib compression/decompression functionality
- History integration
- Encoding auto-detection
- JSON Pretty Print
- Scope filtering
- Intercept feature
- Performance optimizations
