package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import t.mma.hooks.items.hiddenat.HiddenAtSettings
import t.mma.ui.FeatureSettingsProvider

@Composable
internal fun HiddenAtMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { HiddenAtSettings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(HiddenAtSettings.ENABLE, false)) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(scrollBehavior = scrollBehavior, title = provider.title(), largeTitle = provider.title(), bottomBar = {
        BottomActionBar(primaryText = "返回", onPrimaryClick = onBack)
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SettingsCard {
                SwitchRow(enabled, "隐藏艾特", "") {
                    enabled = it; prefs.edit().putBoolean(HiddenAtSettings.ENABLE, it).apply()
                }
            } }
        }
    }
}