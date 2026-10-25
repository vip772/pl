package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.stepmultiplier.StepMultiplierSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun StepMultiplierMiuixPage(context:Context,provider:FeatureSettingsProvider,onBack:()->Unit) {
    val prefs=remember { HchatStorage.preferences(context,StepMultiplierSettings.PREFS) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(StepMultiplierSettings.ENABLE,false)) }
    var factor by remember { mutableStateOf(prefs.getInt(StepMultiplierSettings.FACTOR,1).coerceIn(1,10).toFloat()) }
    val behavior=MiuixScrollBehavior()
    PageScaffold(title=provider.title(),largeTitle=provider.title(),scrollBehavior=behavior,
        bottomBar={ BottomActionBar(primaryText="返回",onPrimaryClick=onBack) }) { padding ->
        LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(
            top=padding.calculateTopPadding()+8.dp,bottom=padding.calculateBottomPadding()+12.dp)) {
            item {
                SettingsCard {
                    Column {
                        SwitchRow(enabled,"步数倍增","调整后续微信运动上报步数") {
                            enabled=it;prefs.edit().putBoolean(StepMultiplierSettings.ENABLE,it).apply()
                        }
                        if(enabled) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("倍率 ${factor.roundToInt()} 倍",color=MiuixTheme.colorScheme.onSurface)
                                Slider(value=factor,onValueChange={
                                    factor=it.roundToInt().coerceIn(1,10).toFloat()
                                    prefs.edit().putInt(StepMultiplierSettings.FACTOR,factor.toInt()).apply()
                                },valueRange=1f..10f,steps=8,modifier=Modifier.fillMaxWidth())
                                Text("例如：10 步 × ${factor.roundToInt()} = ${10*factor.roundToInt()} 步",color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
            item {
                Text("当前仅核查微信 8.0.78。修改在微信下次创建步数上报请求时生效，不会立即改写已同步的排行榜；实际同步结果以微信为准。",
                    color=MiuixTheme.colorScheme.onSurfaceVariantSummary,modifier=Modifier.padding(24.dp))
            }
        }
    }
}