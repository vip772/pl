package h.Hchat.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.colorControls as miuixColorControls
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported as miuixIsRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect as miuixRuntimeShaderEffect
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

fun HchatBackdropEffectScope.hchatColorControls(brightness: Float = 0f, contrast: Float = 1f, saturation: Float = 1f) = miuixColorControls(brightness, contrast, saturation)
fun hchatIsRuntimeShaderSupported(): Boolean = miuixIsRuntimeShaderSupported()

fun HchatBackdropEffectScope.hchatRuntimeShaderEffect(
    key: String,
    shaderString: String,
    uniformShaderName: String,
    block: top.yukonga.miuix.kmp.shader.RuntimeShader.() -> Unit,
) = miuixRuntimeShaderEffect(key, shaderString, uniformShaderName, block)
