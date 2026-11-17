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
    fun isStreet(): Boolean = LocalAppThemeMode.current == AppThemeMode.STREET

    @Composable
    fun canvasBg(): Color = if (isStreet()) DressKitColors.StreetBg else DressKitColors.CreamPaper

    @Composable
    fun cardSurface(): Color = if (isStreet()) DressKitColors.StreetCard else DressKitColors.PureCard

    @Composable
    fun photoBoxSurface(): Color = if (isStreet()) DressKitColors.StreetPhotoSurface else DressKitColors.PureCard

    @Composable
    fun secondarySurface(): Color = if (isStreet()) DressKitColors.StreetSecondary else DressKitColors.WarmStone

    @Composable
    fun borderColor(): Color = if (isStreet()) DressKitColors.StreetBorder else DressKitColors.HairlineBorder

    @Composable
    fun primaryInk(): Color = if (isStreet()) DressKitColors.StreetTextPrimary else DressKitColors.CharcoalInk

    @Composable
    fun secondaryInk(): Color = if (isStreet()) DressKitColors.StreetTextSecondary else DressKitColors.TaupeInk

    @Composable
    fun buttonBg(): Color = if (isStreet()) DressKitColors.StreetAmber else DressKitColors.CharcoalInk

    @Composable
    fun buttonFg(): Color = if (isStreet()) Color(0xFF121418) else DressKitColors.PureCard

    @Composable
    fun accentColor(): Color = if (isStreet()) DressKitColors.StreetAmber else DressKitColors.Terracotta

    @Composable
    fun accentSoft(): Color = if (isStreet()) DressKitColors.StreetAmberSoft else DressKitColors.TerracottaSoft

    @Composable
    fun dockBg(): Color = if (isStreet()) DressKitColors.StreetDock else DressKitColors.FloatingDock

    @Composable
    fun dockActiveBg(): Color = if (isStreet()) DressKitColors.StreetAmber else DressKitColors.FloatingDockActive

    @Composable
    fun dockActiveFg(): Color = if (isStreet()) Color(0xFF121418) else Color.White

    @Composable
    fun dockInactiveFg(): Color = if (isStreet()) Color(0xFF8B929E) else Color(0xFFCCCCCC)

    @Composable
    fun cardCorner(): Dp = if (isStreet()) 20.dp else 18.dp

    @Composable
    fun chipCorner(): Dp = 999.dp

    @Composable
    fun buttonCorner(): Dp = if (isStreet()) 14.dp else 12.dp

    @Composable
    fun borderWidth(): Dp = if (isStreet()) 1.dp else 0.85.dp
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
            colorScheme = if (themeMode == AppThemeMode.STREET) StreetDarkColorScheme else ModernColorScheme,
            typography = CleanCatalogTypography,
            content = content
        )
    }
}
