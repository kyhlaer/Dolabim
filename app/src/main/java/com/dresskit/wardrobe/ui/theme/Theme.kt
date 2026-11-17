package com.dresskit.wardrobe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dresskit.wardrobe.R
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.AppThemeMode

object DressKitColors {
    val CreamPaper = Color(0xFFF6F6F5)
    val PureCard = Color(0xFFFFFFFF)
    val WarmStone = Color(0xFFEDEDEB)
    val HairlineBorder = Color(0xFFE6E6E3)
    val CharcoalInk = Color(0xFF1F1F1D)
    val TaupeInk = Color(0xFF6B6A66)
    val MutedInk = Color(0xFF9C9A95)

    // Modern Dark Palette (Deep warm onyx + stone cards + warm terracotta accent)
    val ModernDarkBg = Color(0xFF161615)
    val ModernDarkCard = Color(0xFF20201E)
    val ModernDarkPhotoSurface = Color(0xFF262623)
    val ModernDarkSecondary = Color(0xFF2A2A27)
    val ModernDarkBorder = Color(0xFF343430)
    val ModernDarkInk = Color(0xFFF3F1ED)
    val ModernDarkTaupe = Color(0xFFA5A19A)
    val ModernDarkTerracotta = Color(0xFFD97A60)
    val ModernDarkTerracottaSoft = Color(0xFF38241F)
    val ModernDarkDock = Color(0xFF232320)
    val ModernDarkDockActive = Color(0xFF3D3D38)

    // Street Dark Theme Palette (Non-white, deep slate + graphite + warm amber)
    val StreetBg = Color(0xFF121418)
    val StreetCard = Color(0xFF1C2026)
    val StreetPhotoSurface = Color(0xFF232831)
    val StreetSecondary = Color(0xFF262B34)
    val StreetBorder = Color(0xFF2F3642)
    val StreetTextPrimary = Color(0xFFF4F3EF)
    val StreetTextSecondary = Color(0xFF9CA3AF)
    val StreetAmber = Color(0xFFF59E0B)
    val StreetAmberSoft = Color(0xFF332610)
    val StreetDock = Color(0xFF1B1F27)
    val StreetDockActive = Color(0xFFF59E0B)

    val FloatingDock = Color(0xFF2B2B29)
    val FloatingDockActive = Color(0xFF4E4E4B)
    val Terracotta = Color(0xFFB65B43)
    val TerracottaSoft = Color(0xFFF5E6E1)
    val OliveSage = Color(0xFF4F6B50)
    val OliveSoft = Color(0xFFE7F0E7)
    val AmberWarm = Color(0xFFB37B22)
    val AmberSoft = Color(0xFFFAF1DF)
    val DangerRust = Color(0xFFA63D33)
}

val LocalAppThemeMode = compositionLocalOf { AppThemeMode.MODERN }
val LocalAppLanguage = compositionLocalOf { AppLanguage.TR }

object DressKitStyle {
    @Composable
    fun mode(): AppThemeMode = LocalAppThemeMode.current

    @Composable
    fun isStreet(): Boolean = LocalAppThemeMode.current == AppThemeMode.STREET

    @Composable
    fun isDark(): Boolean = LocalAppThemeMode.current != AppThemeMode.MODERN

    @Composable
    fun canvasBg(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetBg
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkBg
        AppThemeMode.MODERN -> DressKitColors.CreamPaper
    }

    @Composable
    fun cardSurface(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetCard
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkCard
        AppThemeMode.MODERN -> DressKitColors.PureCard
    }

    @Composable
    fun photoBoxSurface(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetPhotoSurface
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkPhotoSurface
        AppThemeMode.MODERN -> DressKitColors.PureCard
    }

    @Composable
    fun secondarySurface(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetSecondary
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkSecondary
        AppThemeMode.MODERN -> DressKitColors.WarmStone
    }

    @Composable
    fun borderColor(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetBorder
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkBorder
        AppThemeMode.MODERN -> DressKitColors.HairlineBorder
    }

    @Composable
    fun primaryInk(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetTextPrimary
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkInk
        AppThemeMode.MODERN -> DressKitColors.CharcoalInk
    }

    @Composable
    fun secondaryInk(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetTextSecondary
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkTaupe
        AppThemeMode.MODERN -> DressKitColors.TaupeInk
    }

    @Composable
    fun buttonBg(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetAmber
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkInk
        AppThemeMode.MODERN -> DressKitColors.CharcoalInk
    }

    @Composable
    fun buttonFg(): Color = when (mode()) {
        AppThemeMode.STREET -> Color(0xFF121418)
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkBg
        AppThemeMode.MODERN -> DressKitColors.PureCard
    }

    @Composable
    fun accentColor(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetAmber
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkTerracotta
        AppThemeMode.MODERN -> DressKitColors.Terracotta
    }

    @Composable
    fun accentSoft(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetAmberSoft
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkTerracottaSoft
        AppThemeMode.MODERN -> DressKitColors.TerracottaSoft
    }

    @Composable
    fun dockBg(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetDock
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkDock
        AppThemeMode.MODERN -> DressKitColors.FloatingDock
    }

    @Composable
    fun dockActiveBg(): Color = when (mode()) {
        AppThemeMode.STREET -> DressKitColors.StreetAmber
        AppThemeMode.MODERN_DARK -> DressKitColors.ModernDarkDockActive
        AppThemeMode.MODERN -> DressKitColors.FloatingDockActive
    }

    @Composable
    fun dockActiveFg(): Color = when (mode()) {
        AppThemeMode.STREET -> Color(0xFF121418)
        AppThemeMode.MODERN_DARK -> Color.White
        AppThemeMode.MODERN -> Color.White
    }

    @Composable
    fun dockInactiveFg(): Color = when (mode()) {
        AppThemeMode.STREET -> Color(0xFF8B929E)
        AppThemeMode.MODERN_DARK -> Color(0xFF9C9890)
        AppThemeMode.MODERN -> Color(0xFFCCCCCC)
    }

    @Composable
    fun cardCorner(): Dp = if (isStreet()) 20.dp else 18.dp

    @Composable
    fun chipCorner(): Dp = 999.dp

    @Composable
    fun buttonCorner(): Dp = if (isStreet()) 14.dp else 12.dp

    @Composable
    fun borderWidth(): Dp = if (isDark()) 1.dp else 0.85.dp
}

@OptIn(ExperimentalTextApi::class)
val PlusJakartaFontFamily = FontFamily(
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

private val ModernColorScheme = lightColorScheme(
    primary = DressKitColors.CharcoalInk,
    onPrimary = DressKitColors.PureCard,
    primaryContainer = DressKitColors.WarmStone,
    onPrimaryContainer = DressKitColors.CharcoalInk,
    secondary = DressKitColors.Terracotta,
    onSecondary = DressKitColors.PureCard,
    secondaryContainer = DressKitColors.TerracottaSoft,
    onSecondaryContainer = DressKitColors.CharcoalInk,
    tertiary = DressKitColors.OliveSage,
    onTertiary = DressKitColors.PureCard,
    background = DressKitColors.CreamPaper,
    onBackground = DressKitColors.CharcoalInk,
    surface = DressKitColors.PureCard,
    onSurface = DressKitColors.CharcoalInk,
    surfaceVariant = DressKitColors.WarmStone,
    onSurfaceVariant = DressKitColors.TaupeInk,
    outline = DressKitColors.HairlineBorder,
    error = DressKitColors.DangerRust,
    onError = DressKitColors.PureCard
)

private val ModernDarkColorScheme = darkColorScheme(
    primary = DressKitColors.ModernDarkInk,
    onPrimary = DressKitColors.ModernDarkBg,
    primaryContainer = DressKitColors.ModernDarkSecondary,
    onPrimaryContainer = DressKitColors.ModernDarkInk,
    secondary = DressKitColors.ModernDarkTerracotta,
    onSecondary = DressKitColors.ModernDarkBg,
    secondaryContainer = DressKitColors.ModernDarkTerracottaSoft,
    onSecondaryContainer = DressKitColors.ModernDarkTerracotta,
    tertiary = DressKitColors.OliveSage,
    onTertiary = Color.White,
    background = DressKitColors.ModernDarkBg,
    onBackground = DressKitColors.ModernDarkInk,
    surface = DressKitColors.ModernDarkCard,
    onSurface = DressKitColors.ModernDarkInk,
    surfaceVariant = DressKitColors.ModernDarkSecondary,
    onSurfaceVariant = DressKitColors.ModernDarkTaupe,
    outline = DressKitColors.ModernDarkBorder,
    error = Color(0xFFE26D63),
    onError = Color.Black
)

private val StreetDarkColorScheme = darkColorScheme(
    primary = DressKitColors.StreetAmber,
    onPrimary = Color(0xFF121418),
    primaryContainer = DressKitColors.StreetSecondary,
    onPrimaryContainer = DressKitColors.StreetTextPrimary,
    secondary = DressKitColors.StreetAmber,
    onSecondary = Color(0xFF121418),
    secondaryContainer = DressKitColors.StreetAmberSoft,
    onSecondaryContainer = DressKitColors.StreetAmber,
    tertiary = DressKitColors.OliveSage,
    onTertiary = Color.White,
    background = DressKitColors.StreetBg,
    onBackground = DressKitColors.StreetTextPrimary,
    surface = DressKitColors.StreetCard,
    onSurface = DressKitColors.StreetTextPrimary,
    surfaceVariant = DressKitColors.StreetSecondary,
    onSurfaceVariant = DressKitColors.StreetTextSecondary,
    outline = DressKitColors.StreetBorder,
    error = Color(0xFFE26D63),
    onError = Color.Black
)

private val CleanCatalogTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun DressKitTheme(
    themeMode: AppThemeMode = AppThemeMode.MODERN,
    language: AppLanguage = AppLanguage.TR,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppThemeMode provides themeMode,
        LocalAppLanguage provides language
    ) {
        MaterialTheme(
            colorScheme = when (themeMode) {
                AppThemeMode.STREET -> StreetDarkColorScheme
                AppThemeMode.MODERN_DARK -> ModernDarkColorScheme
                AppThemeMode.MODERN -> ModernColorScheme
            },
            typography = CleanCatalogTypography,
            content = content
        )
    }
}
