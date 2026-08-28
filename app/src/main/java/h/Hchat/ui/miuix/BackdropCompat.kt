package h.Hchat.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as miuixRememberLayerBackdrop

/** Local boundary for the blur implementation; callers should not depend on the vendor API. */
typealias HchatBackdrop = Backdrop
typealias HchatLayerBackdrop = LayerBackdrop
typealias HchatBackdropEffectScope = BackdropEffectScope

@Composable
fun rememberHchatLayerBackdrop(graphicsLayer: GraphicsLayer? = null): HchatLayerBackdrop =
    if (graphicsLayer == null) miuixRememberLayerBackdrop() else miuixRememberLayerBackdrop(graphicsLayer)

fun Modifier.hchatLayerBackdrop(backdrop: HchatLayerBackdrop): Modifier =
    miuixLayerBackdrop(backdrop)
