import SwiftUI

/// 肖像画のクレジット（作者・ライセンス）。タップで Commons のファイルページを開く（SPEC §4.4）。
struct PortraitCreditLink: View {
    let portrait: Portrait

    var body: some View {
        let artist = portrait.artist ?? String(localized: "portrait.artistUnknown")
        if let url = URL(string: portrait.sourceUrl) {
            Link(destination: url) {
                Text("portrait.credit \(artist) \(portrait.license)")
                    .font(.footnote)
                    .multilineTextAlignment(.leading)
                    .frame(minHeight: 44)
            }
            .accessibilityHint(Text("portrait.openSource"))
        }
    }
}

/// 画像のクレジット（SPEC §4.9）。端末内のカタログ（シードを含む）の `portrait` から作る（通信しない）。
/// ロック画面・プレイヤーなど、その場で出せないクレジットもここで満たす。
struct ImageCreditsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Section { Text("info.imageCreditsIntro").font(.footnote) }
            if let index = model.index {
                ForEach(Self.credits(index.catalog.composers, english: model.english), id: \.composer.id) { item in
                    CreditRow(name: item.composer.displayName(english: model.english), portrait: item.portrait)
                }
            }
        }
        .navigationTitle(Text("info.imageCredits"))
        .accessibilityIdentifier("imageCredits")
    }

    /// 肖像画のある作曲家を、表示している言語の名前の順に。
    static func credits(_ composers: [Composer], english: Bool) -> [(composer: Composer, portrait: Portrait)] {
        composers.compactMap { c in c.portrait.map { (c, $0) } }
            .sorted {
                $0.composer.displayName(english: english)
                    .localizedStandardCompare($1.composer.displayName(english: english)) == .orderedAscending
            }
    }
}

private struct CreditRow: View {
    let name: String
    let portrait: Portrait

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(name).font(.headline)
            if let title = portrait.title { Text("imageCredit.title \(title)").font(.footnote) }
            Text("imageCredit.artist \(portrait.artist ?? String(localized: "portrait.artistUnknown"))").font(.footnote)
            if let url = portrait.licenseUrl.flatMap(URL.init(string:)) {
                Link(destination: url) { Text("credit.license \(portrait.license)").font(.footnote) }
                    .buttonStyle(.borderless)
                    .frame(minHeight: 44)
            } else {
                Text("credit.license \(portrait.license)").font(.footnote)
            }
            if let url = URL(string: portrait.sourceUrl) {
                Link(destination: url) { Text("imageCredit.source").font(.footnote) }
                    .buttonStyle(.borderless)
                    .frame(minHeight: 44)
            }
        }
        .padding(.vertical, 4)
    }
}
