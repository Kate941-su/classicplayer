package app.cmsl.classical.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {
    @Test
    fun `サンプルは検査を通る`() {
        assertEquals(emptyList<String>(), CatalogValidator.validate(Fixtures.sample()))
    }

    @Test
    fun `異常系のフィクスチャはすべて拒否する`() {
        val invalid = listOf(
            "duplicate-track-id.json",
            "dangling-work-ref.json",
            "foreign-audio-domain.json",
            "schema-version-2.json",
            "trackcount-mismatch.json",
        )
        invalid.forEach { name ->
            val catalog = CatalogJson.decodeCatalog(Fixtures.text("invalid/$name"))
            assertTrue("$name が通ってしまった", CatalogValidator.validate(catalog).isNotEmpty())
        }
    }
}
