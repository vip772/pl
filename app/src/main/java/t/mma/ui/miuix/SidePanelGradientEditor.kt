package t.mma.ui.miuix

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Same three-stop ColorPickerRow used by dynamic recall, embedded in the drawer. */
internal object SidePanelGradientEditor {
    fun mount(activity: Activity, parent: ViewGroup, value: String, changed: (String) -> Unit): () -> Unit {
        val owner = EmbeddedComposeOwner()
        val view = ComposeView(activity)
        owner.install(view)
        owner.attach()
        owner.installComposition(view)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            var colors by remember { mutableStateOf(value) }
            val dark = (activity.resources.configuration.uiMode and 0x30) == 0x20
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                    ColorPickerRow("闪电渐变颜色", "起始色、中间色与结束色", colors,
                        threeColorGradient = true) { colors = it; changed(it) }
                }
            }
        }
        parent.addView(view, ViewGroup.LayoutParams(-1, -2))
        return {
            view.disposeComposition()
            (view.parent as? ViewGroup)?.removeView(view)
            owner.clear(view)
            owner.destroy()
        }
    }
}