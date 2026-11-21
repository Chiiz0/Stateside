package app.stateside.env.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.stateside.env.model.AppThemeMode

private val StatesideLightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F296B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EFF8),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F5FB),
    surfaceContainerHigh = Color(0xFFE9EFF8),
    surfaceContainerHighest = Color(0xFFE0E8F5),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFD9E2EF),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val StatesideDarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF163B70),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF0F4C3A),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0B2545),
    tertiaryContainer = Color(0xFF1E3A68),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF070D19),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0D1628),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF17243D),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF060B15),
    surfaceContainerLow = Color(0xFF0D1628),
    surfaceContainer = Color(0xFF121E36),
    surfaceContainerHigh = Color(0xFF182744),
    surfaceContainerHighest = Color(0xFF203254),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF223352),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val BaseTypography = Typography()
private val StatesideTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.Bold
    ),
    titleSmall = BaseTypography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold
    )
)

@Composable
fun StatesideTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> StatesideDarkColors
        else -> StatesideLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StatesideTypography,
        content = content
    )
}
