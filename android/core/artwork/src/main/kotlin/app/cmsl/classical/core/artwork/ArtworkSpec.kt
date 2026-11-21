package app.cmsl.classical.core.artwork

/**
 * 生成アートワークの設計（SPEC §4.10）。規則の正本は `docs/artwork.md`（iOS と共通）。
 * 作曲家 ID のハッシュから色相と細部を、ジャンルから図形パターンとイラストを決める。外部の画像は使わない。
 */
data class ArtworkSpec(
    /** 0〜359。 */
    val hue: Int,
    val pattern: ArtworkPattern,
    /** 図形の細部（本数・位置）を決める値。0〜255。 */
    val variant: Int,
    /** イラストの種類（`design/genre-art/{illustration}.svg`）。既知の 6 ジャンルの ID か `other`。 */
    val illustration: String = ILLUSTRATION_OTHER,
) {
    companion object {
        private const val FNV_OFFSET = 0x811c9dc5u
        private const val FNV_PRIME = 0x01000193u
        private const val HUE_STEPS = 360u
        private const val VARIANT_SHIFT = 16
        private const val VARIANT_MASK = 0xffu

        fun of(composerId: String, genreId: String): ArtworkSpec {
            val h = fnv1a32(composerId)
            return ArtworkSpec(
                hue = (h % HUE_STEPS).toInt(),
                pattern = ArtworkPattern.forGenre(genreId),
                variant = ((h shr VARIANT_SHIFT) and VARIANT_MASK).toInt(),
                illustration = illustrationFor(genreId),
            )
        }

        const val ILLUSTRATION_OTHER = "other"
        private val ILLUSTRATED = setOf("symphony", "orchestral", "concerto", "piano", "chamber", "vocal")

        /** ジャンル ID → イラストの種類。未知のジャンル（baroque を含む）は「その他」（音符）。 */
        fun illustrationFor(genreId: String): String = if (genreId in ILLUSTRATED) genreId else ILLUSTRATION_OTHER

        /** FNV-1a（32 ビット）。入力は UTF-8 のバイト列。 */
        fun fnv1a32(text: String): UInt {
            var h = FNV_OFFSET
            text.encodeToByteArray().forEach { b ->
                h = (h xor b.toUByte().toUInt()) * FNV_PRIME
            }
            return h
        }
    }
}

enum class ArtworkPattern {
    /** 交響曲: 同心円 */
    Rings,

    /** 管弦楽曲: 波線 */
    Waves,

    /** 協奏曲: 角から広がる弧 */
    Arcs,

    /** ピアノ: 鍵盤の縞 */
    Keys,

    /** 器楽・室内楽: 点の格子 */
    Dots,

    /** 声楽・オペラ: アーチの列 */
    Arches,

    /** バロック: ひし形の格子 */
    Lattice,

    /** その他・未知のジャンル: 斜めの縞 */
    Stripes,
    ;

    companion object {
        fun forGenre(genreId: String): ArtworkPattern = when (genreId) {
            "symphony" -> Rings
            "orchestral" -> Waves
            "concerto" -> Arcs
            "piano" -> Keys
            "chamber" -> Dots
            "vocal" -> Arches
            "baroque" -> Lattice
            else -> Stripes
        }
    }
}
