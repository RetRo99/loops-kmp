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
            url: "https://github.com/RetRo99/loops-kmp/releases/download/0.1.0/LoopsSdk.xcframework.zip",
            checksum: "0538ad659158260eb0f962ade94ee936455dc5e64898d8ab3a40506df460b0f1"
        ),
    ]
)
