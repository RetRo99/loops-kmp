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
            checksum: "ac5cadf96e00a1c1f3f8928d6d32b03dc90de568c58b7013f2d54fe1482a8302"
        ),
    ]
)
