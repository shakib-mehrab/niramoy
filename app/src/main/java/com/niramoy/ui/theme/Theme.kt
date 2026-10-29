package com.niramoy.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.niramoy.R

/**
 * NIRAMOY tokens. Purple + white from the reference shots, pushed to high contrast for older eyes
 * (body text #1E1B3A on white ≈ 16:1, brand #4F44C9 on white ≈ 7:1). State colours are always
 * paired with an icon and a word.
 */
object AppColors {
    val Brand = Color(0xFF4F44C9)
    val BrandLight = Color(0xFF6C63E6)
    val BrandSoft = Color(0xFFE9E7FF)
    val OnBrand = Color.White
    val Background = Color(0xFFF4F3FC)
    val Surface = Color.White
    val SurfaceAlt = Color(0xFFF0EFFA)
    val Text = Color(0xFF1E1B3A)
    val TextMuted = Color(0xFF55526E)
    val Border = Color(0xFFD9D7EC)

    val Taken = Color(0xFF1B7F3B)
    val TakenSoft = Color(0xFFDDF3E4)
    val Missed = Color(0xFFC62828)
    val MissedSoft = Color(0xFFFDE2E1)
    val Skipped = Color(0xFF8A5A00)
    val SkippedSoft = Color(0xFFFFF0CC)
    val Upcoming = Color(0xFF55526E)
    val UpcomingSoft = Color(0xFFEDECF5)
    val Emergency = Color(0xFFD32F2F)
    val WhatsApp = Color(0xFF128C3E)

    val HeroBrush: Brush get() = Brush.linearGradient(listOf(BrandLight, Brand))
}

val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold)
)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

/**
 * Large type scale for elderly users: body ≥ 20sp, headings ≥ 26sp. Sizes are in sp so the phone's
 * own font-size setting still scales everything.
 */
fun appTypography(lang: String): Typography {
    val f = if (lang == "bn") HindSiliguri else Poppins
    return Typography(
        displayLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 54.sp),
        displayMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 48.sp),
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 42.sp),
        headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 38.sp),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 30.sp),
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    )
}

@Composable
fun NiramoyTheme(lang: String, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Brand, onPrimary = AppColors.OnBrand,
            primaryContainer = AppColors.BrandSoft, onPrimaryContainer = AppColors.Brand,
            secondary = AppColors.BrandLight, onSecondary = Color.White,
            background = AppColors.Background, onBackground = AppColors.Text,
            surface = AppColors.Surface, onSurface = AppColors.Text,
            surfaceVariant = AppColors.SurfaceAlt, onSurfaceVariant = AppColors.TextMuted,
            outline = AppColors.Border, error = AppColors.Missed
        ),
        typography = remember(lang) { appTypography(lang) },
        content = content
    )
}
