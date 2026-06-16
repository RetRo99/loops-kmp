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
            url: "https://github.com/RetRo99/loops-kmp/releases/download/0.1.3/LoopsSdk.xcframework.zip",
            checksum: "1eb3269e1ea3bc6ecde59578bc5ca82f2aa24aeac9dabd974edb018f5c4c2d5e"
        ),
    ]
)
