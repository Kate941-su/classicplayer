package app.cmsl.classical.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.R
import app.cmsl.classical.core.model.Composer
import app.cmsl.classical.core.model.Portrait
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import app.cmsl.classical.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.map
import java.text.Collator

data class ImageCredit(val composer: Composer, val portrait: Portrait)

/** 端末内のカタログ（シードを含む）の `portrait` から作る。通信しない（SPEC §4.9）。 */
class ImageCreditsViewModel(container: AppContainer) : ViewModel() {
    val credits = container.catalog.map { s ->
        s?.index?.catalog?.composers?.mapNotNull { c -> c.portrait?.let { ImageCredit(c, it) } }
    }.stateInWhileSubscribed(viewModelScope, null)
}

/** 画像のクレジット（SPEC §4.9）。ロック画面・通知・プレイヤーなど、その場で出せないクレジットもここで満たす。 */
@Composable
fun ImageCreditsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ImageCreditsViewModel(it) }
    val credits by vm.credits.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.image_credits), onBack = onBack) }) { padding ->
        val list = credits ?: return@Scaffold Loading()
        // 作曲家名の順（表示している言語の名前で）
        val sorted = remember(list, english) {
            val collator = Collator.getInstance()
            list.sortedWith(compareBy(collator) { it.composer.displayName(english) })
        }
        LazyColumn(contentPadding = padding, modifier = Modifier.testTag("imageCredits")) {
            item {
                Text(
                    stringResource(R.string.image_credits_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
            }
            items(sorted, key = { it.composer.id }) { credit ->
                CreditRow(credit, english)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CreditRow(credit: ImageCredit, english: Boolean) {
    val uri = LocalUriHandler.current
    val p = credit.portrait
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(credit.composer.displayName(english), style = MaterialTheme.typography.titleMedium)
        val small = MaterialTheme.typography.bodySmall
        p.title?.let { Text(stringResource(R.string.image_credit_title, it), style = small) }
        Text(
            stringResource(R.string.image_credit_artist, p.artist ?: stringResource(R.string.portrait_artist_unknown)),
            style = small,
        )
        val license = stringResource(R.string.credit_license, p.license)
        val licenseUrl = p.licenseUrl
        if (licenseUrl == null) {
            Text(license, style = small)
        } else {
            TextButton(onClick = { uri.openUri(licenseUrl) }) { Text(license, style = small) }
        }
        TextButton(onClick = { uri.openUri(p.sourceUrl) }) {
            Text(stringResource(R.string.image_credit_source), style = small)
        }
    }
}
