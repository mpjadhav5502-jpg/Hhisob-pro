package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = TurmericSecondary,
    onSecondary = Color.White,
    secondaryContainer = TurmericContainer,
    onSecondaryContainer = OnTurmericContainer,
    tertiary = CorianderTertiary,
    onTertiary = Color.White,
    tertiaryContainer = CorianderContainer,
    onTertiaryContainer = OnCorianderContainer,
    error = ChiliError,
    onError = Color.White,
    errorContainer = ChiliContainer,
    onErrorContainer = OnChiliContainer,
    background = CreamBackground,
    onBackground = InkOnBackground,
    surface = WarmWhiteSurface,
    onSurface = InkOnBackground,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaDarkPrimary,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = TerracottaContainer,
    secondary = TurmericDarkSecondary,
    onSecondary = OnTurmericContainer,
    secondaryContainer = Color(0xFF573605),
    onSecondaryContainer = TurmericContainer,
    tertiary = CorianderDarkTertiary,
    onTertiary = OnCorianderContainer,
    tertiaryContainer = Color(0xFF0B4622),
    onTertiaryContainer = CorianderContainer,
    error = ChiliDarkError,
    onError = OnChiliContainer,
    errorContainer = Color(0xFF691212),
    onErrorContainer = ChiliContainer,
    background = EspressoBackground,
    onBackground = CreamOnDark,
    surface = EspressoSurface,
    onSurface = CreamOnDark,
    surfaceVariant = EspressoSurfaceVariant,
    onSurfaceVariant = MutedCreamOnDark,
    outline = Color(0xFF5D483D),
    outlineVariant = Color(0xFF42322A)
)

val MasoorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MasoorBillingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MasoorTypography,
        shapes = MasoorShapes,
        content = content
    )
}
