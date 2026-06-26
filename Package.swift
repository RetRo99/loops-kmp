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
            url: "https://github.com/RetRo99/loops-kmp/releases/download/1.0.1/LoopsSdk.xcframework.zip",
            checksum: "d225a5d33126d1884774c5d17374ba76671bd085e5a80aaf596321fc648f46a8"
        ),
    ]
)
