package jp.vntech.classic.core.search

import jp.vntech.classic.core.model.CatalogIndex
import jp.vntech.classic.core.model.CatalogJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `fixtures/search_cases.json` をすべて通す（iOS と同じケース）。 */
class SearchCasesTest {
    private val fixtures = File(requireNotNull(System.getProperty("fixturesDir")))
    private val cases = CASES_JSON.decodeFromString(
        SearchCases.serializer(),
        File(fixtures, "search_cases.json").readText()
    )
    private val index = SearchIndex(
        CatalogIndex(CatalogJson.decodeCatalog(File(fixtures, "catalog.sample.json").readText()))
    )

    @Test
    fun `正規化のケース`() {
        cases.normalize.forEach { c -> assertEquals(c.input, c.expected, SearchNormalizer.normalize(c.input)) }
    }

    @Test
    fun `検索のケース`() {
        cases.queries.forEach { q ->
            val r = index.search(q.query)
            val found = mapOf(
                "composers" to r.composers.map { it.id },
                "works" to r.works.map { it.id },
                "tracks" to r.tracks.map { it.id },
            )
            if (q.empty) assertTrue("「${q.query}」は空のはず: $found", r.isEmpty)
            q.expect.forEach { (section, ids) ->
                ids.forEach { id ->
                    assertTrue(
                        "「${q.query}」の $section に $id がない: ${found[section]}",
                        id in found.getValue(section)
                    )
                }
            }
            q.absent.forEach { (section, ids) ->
                ids.forEach { id -> assertFalse("「${q.query}」の $section に $id がある", id in found.getValue(section)) }
            }
            q.first.forEach { (section, id) ->
                assertEquals("「${q.query}」の $section の先頭", id, found.getValue(section).firstOrNull())
            }
        }
    }

    private companion object {
        val CASES_JSON = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class SearchCases(val normalize: List<NormalizeCase>, val queries: List<QueryCase>)

    @Serializable
    private data class NormalizeCase(val input: String, val expected: String)

    @Serializable
    private data class QueryCase(
        val query: String,
        val expect: Map<String, List<String>> = emptyMap(),
        val absent: Map<String, List<String>> = emptyMap(),
        val first: Map<String, String> = emptyMap(),
        val empty: Boolean = false,
    )
}
