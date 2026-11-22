import SwiftUI

/// 情報（SPEC §4.9）: アプリについて、CMSL のクレジット（テキストとリンク）、利用条件、プライバシー、
/// カタログの版と手動更新、お問い合わせ、オープンソースライセンス。
struct InfoView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Section("info.about") {
                Text("info.aboutBody")
                Text("info.version \(Self.version)").foregroundStyle(.secondary)
            }
            if let catalog = model.catalog {
                CreditSection(license: catalog.index.catalog.license)
                Section { Text("info.termsBody").font(.footnote) }
                Section {
                    NavigationLink(value: Route.imageCredits) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("info.imageCredits")
                            Text("info.imageCreditsIntro").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("imageCreditsLink")
                }
            }
            Section("info.privacy") {
                Text("info.privacyBody")
                Link(destination: Self.legalPage("")) {
                    Label("info.privacyPolicy", systemImage: "hand.raised")
                }
            }
            if let catalog = model.catalog {
                Section("info.catalog") {
                    Text(Self.catalogDescription(catalog))
                    Button {
                        Task { await model.refreshCatalog() }
                    } label: {
                        Label("action.checkUpdate", systemImage: "arrow.clockwise").frame(minHeight: 44)
                    }
                    .disabled(model.refreshStatus == .checking)
                    if let message = refreshMessage {
                        Text(message).font(.footnote).foregroundStyle(.secondary)
                            .accessibilityAddTraits(.updatesFrequently)
                    }
                }
            }
            Section {
                Link(destination: Self.legalPage("/terms")) {
                    Label("info.termsOfUse", systemImage: "doc.text")
                }
                Link(destination: Self.legalPage("/support")) {
                    Label("info.contact", systemImage: "questionmark.circle")
                }
                NavigationLink(value: Route.licenses) { Text("info.licenses") }
            }
        }
        .navigationTitle(Text("tab.info"))
    }

    private var refreshMessage: LocalizedStringKey? {
        switch model.refreshStatus {
        case .idle: nil
        case .checking: "refresh.checking"
        case .updated: "refresh.updated"
        case .upToDate: "refresh.upToDate"
        case .failed: "refresh.failed"
        case .unsupported: "refresh.unsupported"
        }
    }

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
    }

    /// プライバシーポリシーなどの文書のページ。英語 UI では英語版（末尾に `/en`）。
    static func legalPage(_ path: String) -> URL {
        URL(string: AppModel.legalBaseURL + path + String(localized: "info.legalPageSuffix"))!
    }

    static func catalogDescription(_ catalog: CatalogState) -> String {
        let generated = parseRFC3339(catalog.index.catalog.generatedAt)?.formatted(date: .abbreviated, time: .omitted)
            ?? catalog.index.catalog.generatedAt
        let count = String(localized: "count.tracks \(catalog.index.catalog.tracks.count)")
        switch catalog.source {
        case .seed: return String(localized: "info.catalogSeed \(generated) \(count)")
        case .downloaded: return String(localized: "info.catalogDownloaded \(catalog.catalogVersion ?? "") \(generated) \(count)")
        }
    }
}

/// オープンソースライセンス。外部のライブラリは使っていない（依存を足したらここに載せる）。
struct LicensesView: View {
    var body: some View {
        List { Text("info.licensesBody") }
            .navigationTitle(Text("info.licenses"))
    }
}
