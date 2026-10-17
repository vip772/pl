package t.mma.ui.miuix

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.hooks.items.homesidepanel.HomeSidePanelSettings
import t.mma.hooks.items.homesidepanel.SidePanelAppearance
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Embedded settings use the same components as the main Miuix settings pages. */
internal object SidePanelSettingsEditor {
    fun mount(activity: Activity, parent: ViewGroup, backgroundChanged: () -> Unit,
              pickBackground: () -> Unit, clearBackground: () -> Unit,
              editShortcuts: () -> Unit, effectsChanged: () -> Unit): () -> Unit {
        val owner = EmbeddedComposeOwner()
        val view = ComposeView(activity)
        owner.install(view)
        owner.attach()
        owner.installComposition(view)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        val prefs = HomeSidePanelSettings.preferences(activity)
        view.setContent {
            val dark = (activity.resources.configuration.uiMode and 0x30) == 0x20
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsCard {
                            Column {
                                PreferenceSlider("侧边栏透明度", SidePanelAppearance.OPACITY, .1f..1f, 1f, true,
                                    prefs, backgroundChanged)
                                Action("添加 / 更换背景图", pickBackground)
                                Action("清除背景图", clearBackground)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SettingsCard {
                            Column {
                                var enabled by remember { mutableStateOf(prefs.getBoolean(SidePanelAppearance.EFFECT,false)) }
                                var kind by remember { mutableStateOf(prefs.getString(SidePanelAppearance.EFFECT_KIND,"heart").orEmpty()) }
                                SwitchRow(enabled,"整体动态特效","侧边栏淡入淡出特效") {
                                    enabled=it;prefs.edit().putBoolean(SidePanelAppearance.EFFECT,it).apply();effectsChanged()
                                }
                                if(enabled) PopupChoiceRow("特效样式","选择显示效果",
                                    listOf(PopupChoice("粉色 ❤","heart"),PopupChoice("✨","sparkle")),kind,{
                                        kind=it;prefs.edit().putString(SidePanelAppearance.EFFECT_KIND,it).apply();effectsChanged()
                                    })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SettingsCard {
                            Column {
                                var enabled by remember { mutableStateOf(prefs.getBoolean(SidePanelAppearance.LIGHTNING,false)) }
                                var colors by remember { mutableStateOf(prefs.getString(SidePanelAppearance.COLORS,DynamicRecallGradient.DEFAULT).orEmpty()) }
                                SwitchRow(enabled,"头像动态特效","围绕头像显示动态闪电") {
                                    enabled=it;prefs.edit().putBoolean(SidePanelAppearance.LIGHTNING,it).apply();effectsChanged()
                                }
                                if(enabled) {
                                    PreferenceSlider("闪电粗细",SidePanelAppearance.WIDTH,.5f..4f,1.5f,false,prefs,effectsChanged)
                                    ColorPickerRow("闪电渐变颜色","起始色、中间色与结束色",colors,
                                        threeColorGradient=true,onReset={
                                            colors=DynamicRecallGradient.DEFAULT
                                            prefs.edit().putString(SidePanelAppearance.COLORS,colors).apply();effectsChanged()
                                        }) {
                                        colors=it;prefs.edit().putString(SidePanelAppearance.COLORS,it).apply();effectsChanged()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SettingsCard { Action("快捷栏编辑",editShortcuts) }
                        listOf("clock" to "时间卡片", "photo" to "照片卡片", "shortcuts" to "快捷卡片", "hchat" to "Hchat卡片").forEach { (id,label) ->
                            Spacer(Modifier.height(6.dp))
                            SettingsCard {
                                PreferenceSlider("${label}透明度",SidePanelAppearance.cardOpacity(id),0f..1f,1f,true,prefs,{})
                            }
                        }
                    }
                }
            }
        }
        parent.addView(view,ViewGroup.LayoutParams(-1,-2))
        return {
            view.disposeComposition()
            (view.parent as? ViewGroup)?.removeView(view)
            owner.clear(view)
            owner.destroy()
        }
    }

    @Composable
    private fun Action(label:String, action:()->Unit) {
        Text(text=label,color=MiuixTheme.colorScheme.onSurface,
            modifier=Modifier.fillMaxWidth().clickable(onClick=action).padding(horizontal=16.dp,vertical=16.dp))
    }

    @Composable
    private fun PreferenceSlider(title:String,key:String,range:ClosedFloatingPointRange<Float>,default:Float,
                                 percent:Boolean,prefs:android.content.SharedPreferences,changed:()->Unit) {
        var value by remember(key) { mutableStateOf(prefs.getFloat(key,default).coerceIn(range.start,range.endInclusive)) }
        Column(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp)) {
            val label=if(percent) "${(value*100).toInt()}%" else "${String.format(java.util.Locale.ROOT,"%.1f",value)} dp"
            Text(text="$title  $label",color=MiuixTheme.colorScheme.onSurface)
            Slider(value=value,onValueChange={
                value=it
                prefs.edit().putFloat(key,it).apply()
                changed()
            },valueRange=range,modifier=Modifier.fillMaxWidth())
        }
    }
}
