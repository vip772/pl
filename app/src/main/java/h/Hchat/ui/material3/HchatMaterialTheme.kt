package h.Hchat.ui.material3

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HchatLightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFCFE9DA),
    background = Color(0xFFF8FAF7),
    surface = Color(0xFFF8FAF7),
    surfaceVariant = Color(0xFFDBE5DE)
)

private val HchatDarkColors = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCC0),
    secondaryContainer = Color(0xFF354B40),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF404943)
)

@Composable
fun HchatMaterialTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> HchatDarkColors
        else -> HchatLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
