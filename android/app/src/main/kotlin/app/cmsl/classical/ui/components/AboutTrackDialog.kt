package app.cmsl.classical.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cmsl.classical.R
import app.cmsl.classical.core.model.License
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.displayTitle
import app.cmsl.classical.ui.theme.LocalEnglish

/** 「この曲について」: 原典記事へのリンクとクレジット（SPEC §1.2-5・§4.5）。 */
@Composable
fun AboutTrackDialog(track: Track, license: License, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_about_track)) },
        text = {
            Column {
                Text(track.displayTitle(LocalEnglish.current), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.credit_source, license.credit),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(stringResource(R.string.credit_license, license.name), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.credit_note), style = MaterialTheme.typography.bodySmall)
                // 肖像画を出している曲は、そのクレジットと「画像のクレジット」画面への導線（SPEC §4.8）
                LocalPortraits.current?.portraitOf(track.composerId)?.let { portrait ->
                    Spacer(Modifier.height(8.dp))
                    PortraitCredit(portrait)
                    LocalOpenImageCredits.current?.let { open ->
                        TextButton(onClick = {
                            onDismiss()
                            open()
                        }) { Text(stringResource(R.string.image_credits)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { uri.openUri(track.articleUrl) }
            ) { Text(stringResource(R.string.action_open_article)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
