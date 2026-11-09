import SwiftUI

@main
struct PharmTrackApp: App {
    @StateObject private var app = ReflexApp()

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
        }
    }
}