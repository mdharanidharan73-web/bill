package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BillCircleExtendedColors(
    val heroStart: Color,
    val heroEnd: Color,
    val heroOnSurface: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val cardBorder: Color
)

val LocalBillCircleColors = staticCompositionLocalOf {
    BillCircleExtendedColors(
        heroStart = DeepNavy,
        heroEnd = SlateNavy,
        heroOnSurface = PureWhite,
        success = SuccessGreen,
        successContainer = SuccessSoftBg,
        warning = WarningAmber,
        warningContainer = WarningSoftBg,
        danger = DangerRed,
        dangerContainer = DangerSoftBg,
        info = InfoBlue,
        infoContainer = InfoSoftBg,
        cardBorder = SoftBorder
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldTeal,
    onPrimary = PureWhite,
    primaryContainer = EmeraldSoftBg,
    onPrimaryContainer = DeepNavy,
    secondary = DeepNavy,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = DeepNavy,
    tertiary = WarningAmber,
    onTertiary = PureWhite,
    background = PorcelainWhite,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = InkSecondary,
    outline = Color(0xFFCBD5E1),
    outlineVariant = SoftBorder,
    error = DangerRed,
    onError = PureWhite,
    errorContainer = DangerSoftBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = DeepNavy,
    primaryContainer = EmeraldDeepBg,
    onPrimaryContainer = EmeraldSoftBg,
    secondary = Color(0xFF93C5FD),
    onSecondary = DeepNavy,
    secondaryContainer = NavyContainer,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFFFBBF24),
    onTertiary = DeepNavy,
    background = DarkSurface,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceVariant,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E3A5F),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

val BillCircleShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BillCircleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        BillCircleExtendedColors(
            heroStart = Color(0xFF0A192F),
            heroEnd = Color(0xFF16355C),
            heroOnSurface = PureWhite,
            success = Color(0xFF34D399),
            successContainer = Color(0xFF064E3B),
            warning = Color(0xFFFBBF24),
            warningContainer = Color(0xFF78350F),
            danger = Color(0xFFF87171),
            dangerContainer = Color(0xFF7F1D1D),
            info = Color(0xFF60A5FA),
            infoContainer = Color(0xFF1E3A8A),
            cardBorder = Color(0xFF1E3A5F)
        )
    } else {
        BillCircleExtendedColors(
            heroStart = DeepNavy,
            heroEnd = SlateNavy,
            heroOnSurface = PureWhite,
            success = SuccessGreen,
            successContainer = SuccessSoftBg,
            warning = WarningAmber,
            warningContainer = WarningSoftBg,
            danger = DangerRed,
            dangerContainer = DangerSoftBg,
            info = InfoBlue,
            infoContainer = InfoSoftBg,
            cardBorder = SoftBorder
        )
    }

    CompositionLocalProvider(LocalBillCircleColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = BillCircleShapes,
            content = content
        )
    }
}
