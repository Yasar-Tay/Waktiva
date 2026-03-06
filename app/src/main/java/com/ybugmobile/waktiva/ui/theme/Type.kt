package com.ybugmobile.waktiva.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R

// --- Font Families ---

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_light, FontWeight.Light),
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold)
)

val IBMPlexArabic = FontFamily(
    Font(R.font.ibm_plex_arabic_light, FontWeight.Light),
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold)
)

// --- Composed Typography Factory ---

private fun createComposedTypography(
    displayFont: FontFamily,
    headlineFont: FontFamily,
    titleFont: FontFamily,
    bodyFont: FontFamily
): Typography {
    return Typography(
        // DISPLAY: Large numbers and hero text
        displayLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
        
        // HEADLINES: Structural section headers
        headlineLarge = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
        
        // TITLES: Card headers and navigation
        titleLarge = TextStyle(fontFamily = titleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = titleFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = titleFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        
        // BODY: Main reading text
        bodyLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        
        // LABELS: Functional UI elements and metadata
        labelLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    )
}

// --- Specific Compositions ---

// 1. Default (Latin/Global): High-end mix
// Display: IBM Plex (Geometric/Tech) | Headline: Hind (Modern) | Title: Inter (UI) | Body: Inter (Legibility)
val DefaultComposedTypography = createComposedTypography(
    displayFont = IBMPlexArabic,
    headlineFont = HindSiliguri,
    titleFont = Inter,
    bodyFont = Inter
)

// 2. Arabic Locale: Coherent script-focused design
val ArabicComposedTypography = createComposedTypography(
    displayFont = IBMPlexArabic,
    headlineFont = IBMPlexArabic,
    titleFont = IBMPlexArabic,
    bodyFont = IBMPlexArabic
)

// 3. Bengali Locale: Script-focused with geometric accents
val BengaliComposedTypography = createComposedTypography(
    displayFont = IBMPlexArabic, // Numbers still look great in Plex
    headlineFont = HindSiliguri,
    titleFont = HindSiliguri,
    bodyFont = HindSiliguri
)

// Keep this for backward compatibility and internal tool usage
val Typography = DefaultComposedTypography
