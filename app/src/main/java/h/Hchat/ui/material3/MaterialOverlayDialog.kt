package h.Hchat.ui.material3

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import h.Hchat.ui.miuix.EmbeddedComposeOwnerInstaller
import java.util.concurrent.atomic.AtomicBoolean

enum class MaterialDialogPosition { TOP, CENTER, BOTTOM }

interface MaterialOverlayHandle {
    fun close()
    fun isShowing(): Boolean
}

@Composable
fun MaterialOverlayDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    position: MaterialDialogPosition = MaterialDialogPosition.BOTTOM,
    content: @Composable () -> Unit
) {
    if (!show) return
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state, isBackEnabled = true, onBackCompleted = onDismissRequest)
    val alignment = when (position) {
        MaterialDialogPosition.TOP -> Alignment.TopCenter
        MaterialDialogPosition.CENTER -> Alignment.Center
        MaterialDialogPosition.BOTTOM -> Alignment.BottomCenter
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.8f).coerceAtLeast(240.dp)
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
            .padding(12.dp)
    ) {
        Column(
            Modifier.align(alignment).widthIn(max = 420.dp).fillMaxWidth().heightIn(max = maxHeight)
                .pointerInput(Unit) { detectTapGestures { } }
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp)
        ) {
            if (title.isNotBlank()) Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            content()
        }
    }
}

fun showMaterialOverlay(
    activity: Activity,
    onDismiss: () -> Unit = {},
    content: @Composable (() -> Unit) -> Unit
): MaterialOverlayHandle {
    val decor = activity.window?.decorView as? ViewGroup ?: return EmptyMaterialOverlayHandle
    val owner = MaterialComposeOwner()
    val closed = AtomicBoolean(false)
    val root = FrameLayout(activity).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    owner.install(decor); owner.install(root); owner.attach()
    lateinit var compose: ComposeView
    val close: () -> Unit = close@{
        if (!closed.compareAndSet(false, true)) return@close
        val cleanup = {
            runCatching { compose.disposeComposition() }
            runCatching { (root.parent as? ViewGroup)?.removeView(root) }
            owner.clear(root); owner.clear(decor); owner.destroy(); onDismiss()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else activity.runOnUiThread { cleanup() }
    }
    compose = ComposeView(activity).apply {
        owner.install(this)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                HchatMaterialTheme(darkTheme = dark) { content(close) }
            }
        }
    }
    root.addView(compose, FrameLayout.LayoutParams(-1, -1)); decor.addView(root); root.requestFocus()
    return object : MaterialOverlayHandle {
        override fun close() = close()
        override fun isShowing() = !closed.get()
    }
}

private object EmptyMaterialOverlayHandle : MaterialOverlayHandle {
    override fun close() = Unit
    override fun isShowing() = false
}

private class MaterialComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, NavigationEventDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val navigationDispatcher = NavigationEventDispatcher()
    private var restored = false
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val navigationEventDispatcher: NavigationEventDispatcher get() = navigationDispatcher
    fun install(view: View) = EmbeddedComposeOwnerInstaller.install(view, this, this, this, this)
    fun clear(view: View) = EmbeddedComposeOwnerInstaller.clear(view)
    fun attach() {
        if (!restored) { savedStateController.performRestore(Bundle.EMPTY); restored = true }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    fun destroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        navigationDispatcher.dispose(); store.clear()
    }
}
