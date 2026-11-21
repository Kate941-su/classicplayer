import SwiftUI

@main
struct CMSLClassicalApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = AppModel.live(uiTesting: ProcessInfo.processInfo.arguments.contains("-uiTesting"))

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                // UI テストのスクリーンショット用（ダークテーマ）
                .preferredColorScheme(ProcessInfo.processInfo.arguments.contains("-uiDarkMode") ? .dark : nil)
                .task { model.start() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { model.saveState() }
        }
    }
}
