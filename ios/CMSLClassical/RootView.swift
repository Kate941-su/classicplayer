import SwiftUI

/// タブ（ホーム・検索・ライブラリ・情報）。ミニプレイヤーはタブバーの上に出す（SPEC §4.1）。
struct RootView: View {
    @Environment(AppModel.self) private var model
    @State private var showPlayer = false

    var body: some View {
        TabView {
            tab { HomeView() }
                .tabItem { Label("tab.home", systemImage: "music.note.house") }
            tab { SearchView() }
                .tabItem { Label("tab.search", systemImage: "magnifyingglass") }
            tab { LibraryView() }
                .tabItem { Label("tab.library", systemImage: "star") }
            tab { InfoView() }
                .tabItem { Label("tab.info", systemImage: "info.circle") }
        }
        .sheet(isPresented: $showPlayer) { PlayerView() }
        .overlay(alignment: .top) { NoticeBanner() }
    }

    private func tab(@ViewBuilder _ content: () -> some View) -> some View {
        NavigationStack {
            content().navigationDestination(for: Route.self) { RouteView(route: $0) }
        }
        .miniPlayerInset(showPlayer: $showPlayer)
    }
}

/// 再生できなかったときのお知らせ（Android のトーストに当たる）。3 秒で消える。
private struct NoticeBanner: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let notice = model.playback.notice
        Group {
            if let notice {
                Text(notice == .skipped ? "playback.errorSkipped" : "playback.errorStopped")
                    .font(.footnote)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .accessibilityAddTraits(.isStaticText)
                    .onAppear {
                        UIAccessibility.post(notification: .announcement, argument: String(
                            localized: notice == .skipped ? "playback.errorSkipped" : "playback.errorStopped"
                        ))
                    }
            }
        }
        .animation(.default, value: notice)
        .task(id: notice) {
            guard notice != nil else { return }
            try? await Task.sleep(for: .seconds(3))
            model.playback.clearNotice()
        }
    }
}
