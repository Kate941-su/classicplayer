package jp.vntech.classic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

// 落ち着いた「コンサートホール」（SPEC §4.11）: ワインレッドの幕と、金の装飾。文字とのコントラストは WCAG AA 以上
private val Light = lightColorScheme(
    primary = Color(0xFF7A2E3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DADC),
    onPrimaryContainer = Color(0xFF3B0E16),
    secondary = Color(0xFF7A5C22),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E2C2),
    onSecondaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFFBF7F2),
    onBackground = Color(0xFF1F1A17),
    surface = Color(0xFFFBF7F2),
    onSurface = Color(0xFF1F1A17),
    surfaceVariant = Color(0xFFEDE3DB),
    onSurfaceVariant = Color(0xFF4F4540),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1EB),
    surfaceContainer = Color(0xFFF3ECE5),
    surfaceContainerHigh = Color(0xFFEDE5DD),
    surfaceContainerHighest = Color(0xFFE7DED6),
    surfaceTint = Color(0xFF7A2E3A),
    outline = Color(0xFF81756E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0B5BE),
    onPrimary = Color(0xFF4F1622),
    primaryContainer = Color(0xFF6A2733),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFE8C987),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4410),
    onSecondaryContainer = Color(0xFFFFE08F),
    background = Color(0xFF16110F),
    onBackground = Color(0xFFEDE0DA),
    surface = Color(0xFF16110F),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF4F4540),
    onSurfaceVariant = Color(0xFFD3C4BC),
    surfaceContainerLowest = Color(0xFF110C0A),
    surfaceContainerLow = Color(0xFF1E1714),
    surfaceContainer = Color(0xFF221B18),
    surfaceContainerHigh = Color(0xFF2D2522),
    surfaceContainerHighest = Color(0xFF38302C),
    surfaceTint = Color(0xFFF0B5BE),
    outline = Color(0xFF9C8E87),
)

/** 英語 UI か（`nameEn` / `titleEn` を優先するか。SPEC §4.11）。 */
val LocalEnglish = staticCompositionLocalOf { false }

@Composable
fun CmslTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val english = LocalConfiguration.current.locales[0].language != "ja"
    CompositionLocalProvider(LocalEnglish provides english) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
    }
}
