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
 * NIRAMOY tokens: calm powder-blue health palette (soft blue background, airy cards,
 * slate-teal accent). Text stays dark slate on light surfaces for comfortable contrast
 * (Text on Surface ≈ 12:1, Brand on Surface ≈ 4.8:1). State colours are always paired with an icon and a word.
 */
object AppColors {
    val Brand = Color(0xFF47748C)
    val BrandLight = Color(0xFF7FA7BC)
    val BrandSoft = Color(0xFFDDEAF2)
    val OnBrand = Color.White
    val Background = Color(0xFFD9E7F0)
    val BackgroundTop = Color(0xFFC7DBE8)
    val Surface = Color(0xFFF5F9FC)
    val SurfaceAlt = Color(0xFFE6EFF5)
    val Text = Color(0xFF22313B)
    val TextMuted = Color(0xFF5E717D)
    val Border = Color(0xFFCADAE5)

    val Taken = Color(0xFF2E8467)
    val TakenSoft = Color(0xFFDCF0E7)
    val Missed = Color(0xFFC9524A)
    val MissedSoft = Color(0xFFF8E1DE)
    val Skipped = Color(0xFFA87518)
    val SkippedSoft = Color(0xFFF6ECD6)
    val Upcoming = Color(0xFF5E717D)
    val UpcomingSoft = Color(0xFFE3ECF2)
    val Emergency = Color(0xFFD14B45)
    val WhatsApp = Color(0xFF1F9D55)
    val Heart = Color(0xFFE56B6F)

    /** Page background: soft vertical wash, like the reference. */
    val BackgroundBrush: Brush get() = Brush.verticalGradient(listOf(BackgroundTop, Background, Color(0xFFE4EEF4)))
    /** Accent card / hero gradient. */
    val HeroBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFF6A97AE), Brand))
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
 * Modern type scale. [scale] is 1.0 by default and 1.2 with Settings > "Larger text" (for older eyes);
 * sizes are in sp, so the phone's own font size setting applies on top.
 */
fun appTypography(lang: String, scale: Float = 1f): Typography {
    val f = if (lang == "bn") HindSiliguri else Poppins
    // Bangla glyphs need a little more size and line height to read as large as Latin.
    val k = scale * if (lang == "bn") 1.08f else 1f
    fun s(size: Float, line: Float, w: FontWeight, ls: Float = 0f) =
        TextStyle(fontFamily = f, fontWeight = w, fontSize = (size * k).sp, lineHeight = (line * k).sp, letterSpacing = ls.sp)
    return Typography(
        displayLarge = s(40f, 48f, FontWeight.SemiBold, -0.5f),
        displayMedium = s(34f, 42f, FontWeight.SemiBold, -0.4f),
        displaySmall = s(28f, 36f, FontWeight.SemiBold, -0.3f),
        headlineLarge = s(26f, 34f, FontWeight.SemiBold, -0.2f),
        headlineMedium = s(23f, 31f, FontWeight.SemiBold),
        headlineSmall = s(21f, 29f, FontWeight.SemiBold),
        titleLarge = s(19f, 27f, FontWeight.SemiBold),
        titleMedium = s(17f, 25f, FontWeight.Medium),
        titleSmall = s(15f, 22f, FontWeight.Medium),
        bodyLarge = s(16f, 24f, FontWeight.Normal),
        bodyMedium = s(15f, 22f, FontWeight.Normal),
        bodySmall = s(13f, 19f, FontWeight.Normal),
        labelLarge = s(15f, 21f, FontWeight.SemiBold),
        labelMedium = s(13f, 18f, FontWeight.Medium),
        labelSmall = s(12f, 16f, FontWeight.Medium, 0.2f)
    )
}

@Composable
fun NiramoyTheme(lang: String, textScale: Float = 1f, content: @Composable () -> Unit) {
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
            surfaceContainerHigh = AppColors.Surface, surfaceContainerHighest = AppColors.SurfaceAlt,
            outline = AppColors.Border, error = AppColors.Missed
        ),
        typography = remember(lang, textScale) { appTypography(lang, textScale) },
        content = content
    )
}
