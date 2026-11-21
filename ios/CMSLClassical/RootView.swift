import SwiftUI

/// 仮の画面。PLAN 2.7 で TabView と NavigationStack の UI に置き換える。
struct RootView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "music.note")
                .font(.largeTitle)
                .accessibilityHidden(true)
            Text("app.name")
                .font(.title2)
            Text("credit.cmsl")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

#Preview {
    RootView()
}
