// swift-tools-version:5.9
import PackageDescription

// This file is updated automatically by .github/workflows/release.yml on each tag.
// The url + checksum point at the XCFramework attached to the matching GitHub Release.
let package = Package(
    name: "LoopsSdk",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(name: "LoopsSdk", targets: ["LoopsSdk"]),
    ],
    targets: [
        .binaryTarget(
            name: "LoopsSdk",
            url: "https://github.com/RetRo99/loops-kmp/releases/download/0.1.2/LoopsSdk.xcframework.zip",
            checksum: "7292b1680a010c8d2c9933e30d235071b1544fc5b49401d7a21a31a6b45acbf8"
        ),
    ]
)
