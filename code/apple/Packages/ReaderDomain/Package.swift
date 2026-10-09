// swift-tools-version: 6.0
import PackageDescription

// Dominio de la app de Apple (Mac e iOS): modelos y reglas sin UI ni frameworks de Apple.
// Se testea con `swift test` (requiere Xcode 16 o posterior).
let package = Package(
    name: "ReaderDomain",
    platforms: [.macOS(.v14), .iOS(.v17)],
    products: [
        .library(name: "ReaderDomain", targets: ["ReaderDomain"]),
    ],
    targets: [
        .target(name: "ReaderDomain"),
        .testTarget(name: "ReaderDomainTests", dependencies: ["ReaderDomain"]),
    ]
)
