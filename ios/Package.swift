// swift-tools-version:5.9
import PackageDescription

// Phase 2: pure Swift Core + headless runner. No SwiftUI, no UIKit, no Combine, no Skip.
// Yams is a TOOL dependency only — Core receives plain values (contracts/runner.yaml).
let package = Package(
    name: "ReflexIOS",
    platforms: [.macOS(.v13), .iOS(.v17)],
    products: [
        .library(name: "ReflexCore", targets: ["Core"]),
        .executable(name: "reflex-runner", targets: ["reflex-runner"]),
    ],
    dependencies: [
        .package(url: "https://github.com/jpsim/Yams.git", from: "5.1.3"),
    ],
    targets: [
        .target(name: "Core", path: "Sources/Core"),
        .target(name: "RunnerKit", dependencies: ["Core", "Yams"], path: "Tools/RunnerKit"),
        .executableTarget(name: "reflex-runner", dependencies: ["RunnerKit"], path: "Tools/reflex-runner"),
        .testTarget(name: "BehaviorTests", dependencies: ["Core", "RunnerKit"], path: "Tests/BehaviorTests"),
    ]
)