package org.drishti.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = PureBlack,
    secondary = SafetyCyan,
    onSecondary = PureBlack,
    tertiary = WarningOrange,
    background = PureBlack,
    onBackground = PureWhite,
    surface = PureBlack,
    onSurface = PureWhite,
    error = CriticalRed,
    onError = PureWhite
)

@Composable
fun DrishtiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HighContrastDarkColorScheme,
        content = content
    )
}
