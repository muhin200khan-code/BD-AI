package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentOption
import com.example.data.model.ThemePreference

val BdAiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun buildColorScheme(darkTheme: Boolean, accent: AccentOption): ColorScheme {
    return if (darkTheme) {
        val (primary, container, onContainer, secondary) = when (accent) {
            AccentOption.EMERALD -> listOf(EmeraldPrimaryDark, EmeraldContainerDark, EmeraldOnContainerDark, CyanPrimaryDark)
            AccentOption.INDIGO -> listOf(IndigoPrimaryDark, IndigoContainerDark, Color(0xFFE0E7FF), CyanPrimaryDark)
            AccentOption.CYAN -> listOf(CyanPrimaryDark, CyanContainerDark, Color(0xFFCFFAFE), EmeraldPrimaryDark)
            AccentOption.ROSE -> listOf(RosePrimaryDark, RoseContainerDark, Color(0xFFFFE4E6), AmberPrimaryDark)
            AccentOption.AMBER -> listOf(AmberPrimaryDark, AmberContainerDark, Color(0xFFFEF3C7), EmeraldPrimaryDark)
        }
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF041410),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = secondary,
            onSecondary = Color(0xFF041410),
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = DarkOnSurface,
            tertiary = CyanPrimaryDark,
            background = DarkBackground,
            onBackground = DarkOnSurface,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = Color(0xFF060A11),
            surfaceContainerLow = Color(0xFF0C1220),
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = Color(0xFF172033),
            surfaceContainerHighest = DarkSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = Color(0xFF1E293B),
            error = Color(0xFFF87171),
            onError = Color(0xFF450A0A)
        )
    } else {
        val (primary, container, onContainer, secondary) = when (accent) {
            AccentOption.EMERALD -> listOf(EmeraldPrimaryLight, EmeraldContainerLight, EmeraldOnContainerLight, CyanPrimaryLight)
            AccentOption.INDIGO -> listOf(IndigoPrimaryLight, IndigoContainerLight, Color(0xFF1E1B4B), CyanPrimaryLight)
            AccentOption.CYAN -> listOf(CyanPrimaryLight, CyanContainerLight, Color(0xFF083344), EmeraldPrimaryLight)
            AccentOption.ROSE -> listOf(RosePrimaryLight, RoseContainerLight, Color(0xFF4C0519), AmberPrimaryLight)
            AccentOption.AMBER -> listOf(AmberPrimaryLight, AmberContainerLight, Color(0xFF451A03), EmeraldPrimaryLight)
        }
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = LightOnSurface,
            tertiary = CyanPrimaryLight,
            background = LightBackground,
            onBackground = LightOnSurface,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF1F5F9),
            surfaceContainer = Color(0xFFF8FAFC),
            surfaceContainerHigh = Color(0xFFEFF4F9),
            surfaceContainerHighest = LightSurfaceVariant,
            outline = LightOutline,
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }
}

@Composable
fun BdAiTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    accentOption: AccentOption = AccentOption.EMERALD,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(
        colorScheme = buildColorScheme(darkTheme = useDark, accent = accentOption),
        typography = BdAiTypography,
        shapes = BdAiShapes,
        content = content
    )
}
