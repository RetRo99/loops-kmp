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
            url: "https://github.com/retro99/loops-kmp/releases/download/0.0.0/LoopsSdk.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
