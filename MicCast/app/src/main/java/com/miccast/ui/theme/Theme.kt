package com.miccast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

// ── Brand colors (purple palette matching the design) ──────────────────────────
val Purple10  = Color(0xFF21005D)
val Purple20  = Color(0xFF381E72)
val Purple30  = Color(0xFF4F378B)
val Purple40  = Color(0xFF6750A4)
val Purple80  = Color(0xFFD0BCFF)
val Purple90  = Color(0xFFEADDFF)
val Purple95  = Color(0xFFF6EDFF)
val Purple99  = Color(0xFFFFFBFE)

val PurpleGrey40 = Color(0xFF625B71)
val PurpleGrey80 = Color(0xFFCCC2DC)
val PurpleGrey90 = Color(0xFFEADDFF)

val Pink40 = Color(0xFF7D5260)
val Pink80 = Color(0xFFEFB8C8)
val Pink90 = Color(0xFFFFD8E4)

private val LightColorScheme = lightColorScheme(
    primary       = Purple40,
    onPrimary     = Color.White,
    primaryContainer    = Purple90,
    onPrimaryContainer  = Purple10,
    secondary     = PurpleGrey40,
    onSecondary   = Color.White,
    secondaryContainer  = PurpleGrey90,
    onSecondaryContainer = Purple10,
    tertiary      = Pink40,
    onTertiary    = Color.White,
    tertiaryContainer   = Pink90,
    onTertiaryContainer = Color(0xFF31111D),
    background    = Purple99,
    onBackground  = Purple10,
    surface       = Purple99,
    onSurface     = Purple10,
    surfaceVariant      = Color(0xFFE7E0EC),
    onSurfaceVariant    = Color(0xFF49454F),
    outline       = Color(0xFF79747E),
)

private val DarkColorScheme = darkColorScheme(
    primary       = Purple80,
    onPrimary     = Purple20,
    primaryContainer    = Purple30,
    onPrimaryContainer  = Purple90,
    secondary     = PurpleGrey80,
    onSecondary   = Color(0xFF332D41),
    secondaryContainer  = Color(0xFF4A4458),
    onSecondaryContainer = PurpleGrey90,
    tertiary      = Pink80,
    onTertiary    = Color(0xFF492532),
    tertiaryContainer   = Color(0xFF633B48),
    onTertiaryContainer = Pink90,
)

@Composable
fun MicCastTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
