package app.cmsl.classical.ui.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.BuildConfig
import app.cmsl.classical.R
import app.cmsl.classical.core.data.CatalogSource
import app.cmsl.classical.core.data.CatalogState
import app.cmsl.classical.core.data.RefreshResult
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.components.SectionHeader
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

enum class RefreshStatus { Idle, Checking, Updated, UpToDate, Failed, Unsupported }

data class InfoUiState(val catalog: CatalogState, val refresh: RefreshStatus)

class InfoViewModel(private val container: AppContainer) : ViewModel() {
    private val refresh = MutableStateFlow(RefreshStatus.Idle)

    val state = combine(container.catalog, refresh) { c, r -> c?.let { InfoUiState(it, r) } }
        .stateInWhileSubscribed(viewModelScope, null)

    /** 手動更新: 24 時間の制限を無視する（SPEC §7）。 */
    fun refresh() {
        if (refresh.value == RefreshStatus.Checking) return
        refresh.value = RefreshStatus.Checking
        viewModelScope.launch {
            refresh.value = when (container.refreshCatalog(force = true)) {
                is RefreshResult.Updated -> RefreshStatus.Updated
                RefreshResult.NotModified, RefreshResult.UpToDate, RefreshResult.Skipped -> RefreshStatus.UpToDate
                RefreshResult.Unsupported -> RefreshStatus.Unsupported
                is RefreshResult.Failed -> RefreshStatus.Failed
            }
        }
    }
}

@Composable
fun InfoScreen(onLicenses: () -> Unit, onImageCredits: () -> Unit) {
    val vm = containerViewModel { InfoViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.tab_info)) }) { padding ->
        val s = state ?: return@Scaffold Loading()
        val license = s.catalog.index.catalog.license
        val privacyUrl = PRIVACY_URL_PATH.localized()
        val supportUrl = SUPPORT_URL_PATH.localized()
        LazyColumn(contentPadding = padding, modifier = Modifier.testTag("info")) {
            item {
                SectionHeader(stringResource(R.string.info_about))
                Paragraph(stringResource(R.string.info_about_body))
                Paragraph(stringResource(R.string.info_version, BuildConfig.VERSION_NAME))
            }
            item {
                SectionHeader(stringResource(R.string.info_credit))
                Paragraph(stringResource(R.string.credit_source, license.credit))
                Paragraph(stringResource(R.string.credit_license, license.name))
                LinkRow(stringResource(R.string.info_cmsl_site)) { uri.openUri(license.url) }
                Paragraph(stringResource(R.string.info_terms_body))
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onImageCredits),
                    headlineContent = { Text(stringResource(R.string.image_credits)) },
                    supportingContent = { Text(stringResource(R.string.image_credits_intro)) },
                )
            }
            item {
                SectionHeader(stringResource(R.string.info_privacy))
                Paragraph(stringResource(R.string.info_privacy_body))
                LinkRow(stringResource(R.string.info_privacy_policy)) { uri.openUri(privacyUrl) }
            }
            item {
                SectionHeader(stringResource(R.string.info_catalog))
                Paragraph(catalogDescription(s.catalog))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = vm::refresh, enabled = s.refresh != RefreshStatus.Checking) {
                        Text(stringResource(R.string.action_check_update))
                    }
                    refreshMessage(s.refresh)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LinkRow(stringResource(R.string.info_contact)) { uri.openUri(supportUrl) }
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLicenses),
                    headlineContent = { Text(stringResource(R.string.info_licenses)) },
                )
            }
        }
    }
}

@Composable
private fun String.localized(): String = SITE_BASE + this + stringResource(R.string.site_page_suffix)

@Composable
private fun catalogDescription(c: CatalogState): String {
    val generated = formatDate(c.index.catalog.generatedAt)
    val n = c.index.catalog.tracks.size
    val count = pluralStringResource(R.plurals.track_count, n, n)
    return when (c.source) {
        CatalogSource.Seed -> stringResource(R.string.info_catalog_seed, generated, count)
        CatalogSource.Downloaded ->
            stringResource(
                R.string.info_catalog_downloaded,
                c.catalogVersion.orEmpty(),
                generated,
                count
            )
    }
}

@Composable
private fun refreshMessage(status: RefreshStatus): String? = when (status) {
    RefreshStatus.Idle -> null
    RefreshStatus.Checking -> stringResource(R.string.refresh_checking)
    RefreshStatus.Updated -> stringResource(R.string.refresh_updated)
    RefreshStatus.UpToDate -> stringResource(R.string.refresh_up_to_date)
    RefreshStatus.Failed -> stringResource(R.string.refresh_failed)
    RefreshStatus.Unsupported -> stringResource(R.string.refresh_unsupported)
}

private fun formatDate(rfc3339: String): String = try {
    OffsetDateTime.parse(rfc3339).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: DateTimeParseException) {
    rfc3339
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
    )
}

private const val SITE_BASE = "https://kate941-su.github.io/classicplayer/"
private const val PRIVACY_URL_PATH = "privacy/"
private const val SUPPORT_URL_PATH = "support/"

/** オープンソースライセンス一覧。依存を追加したらここにも足す（ライセンスは MIT/Apache/BSD のみ）。 */
private val LICENSES = listOf(
    "AndroidX (Activity, Compose, Core, DataStore, Lifecycle, Media3, Navigation, Room)" to "Apache License 2.0",
    "Kotlin / kotlinx.coroutines / kotlinx.serialization" to "Apache License 2.0",
    "OkHttp / Okio" to "Apache License 2.0",
    "Guava (via Media3)" to "Apache License 2.0",
    "Material Icons" to "Apache License 2.0",
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.info_licenses), onBack = onBack) }) { padding ->
        LazyColumn(contentPadding = padding) {
            items(LICENSES) { (name, license) ->
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(license) })
            }
        }
    }
}
