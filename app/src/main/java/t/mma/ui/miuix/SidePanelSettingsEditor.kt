package t.mma.ui.miuix

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
              editShortcuts: () -> Unit, effectsChanged: () -> Unit, registerBack: ((() -> Boolean)?) -> Unit, pageChanged: (Boolean) -> Unit): () -> Unit {
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
                    var cardPage by remember { mutableStateOf(false) }
                    var revision by remember { mutableStateOf(0) }
                    DisposableEffect(Unit) {
                        registerBack { if(cardPage) { cardPage=false;pageChanged(false);true } else false }
                        onDispose { registerBack(null) }
                    }
                    AnimatedContent(targetState=cardPage,transitionSpec={
                        (slideInHorizontally(tween(220)) { if(targetState) it else -it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { if(targetState) -it else it } + fadeOut(tween(180)))
                    },label="侧栏卡片设置") { cards ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                    if(cards) {
                        Action("‹  卡片设置") { cardPage=false;pageChanged(false) }
                        val extras=remember(revision) { SidePanelAppearance.extraPhotos(activity) }
                        val entries=listOf("clock" to "时间卡片","photo" to "图片卡片","shortcuts" to "快捷卡片","hchat" to "Hchat卡片", "weather" to "天气卡片") + extras.mapIndexed { index,id -> id to "图片卡片 ${index+2}" }
                        entries.forEach { (id,label) ->
                            key(id) {
                            SettingsCard {
                                Column {
                                    var cardEnabled by remember(id) { mutableStateOf(prefs.getBoolean(SidePanelAppearance.cardEnabled(id), id != "weather")) }
                                    SwitchRow(cardEnabled, label, "关闭后侧边栏不显示此卡片") { cardEnabled = it; prefs.edit().putBoolean(SidePanelAppearance.cardEnabled(id), it).apply() }
                                    if(id == "weather") WeatherCityEditor(activity)
                                    PreferenceSlider("${label}透明度",SidePanelAppearance.cardOpacity(id),0f..1f,1f,true,prefs,{})
                                    if(SidePanelAppearance.isPhoto(id)) {
                                        PreferenceHeight(id,activity)
                                        if(id == "photo") Action("添加卡片") { SidePanelAppearance.addPhoto(activity);revision++ }
                                        else Action("移除卡片") { SidePanelAppearance.removePhoto(activity,id);revision++ }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            }
                        }
                    } else {
                        SettingsCard { Action("卡片设置") { cardPage=true;pageChanged(true) } }
                        Spacer(Modifier.height(6.dp))

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
    private fun WeatherCityEditor(activity: Activity) {
        val prefs = HomeSidePanelSettings.preferences(activity)
        var city by remember { mutableStateOf(prefs.getString("weather_city", "").orEmpty()) }
        var query by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        InputRow(title = "天气城市", summary = city.ifBlank { "输入城市名称，点击搜索选择" }, value = query, onValueChange = { query = it.take(80) })
        Action(if(busy) "正在搜索…" else "搜索并选择城市") {
            if(!busy && query.isNotBlank()) {
                busy = true
                t.mma.hooks.items.homeextension.HomeWeather.search(query.trim()) { cities ->
                    busy = false
                    if(!activity.isFinishing && !activity.isDestroyed) {
                        if(cities.isNullOrEmpty()) android.widget.Toast.makeText(activity, "未找到城市或网络暂不可用，请尝试拼音", android.widget.Toast.LENGTH_SHORT).show()
                        else VoiceForwardMiuixDialog.showChoices(activity, "选择天气城市", "侧边栏城市独立保存", cities.map { it.name to "${it.latitude}, ${it.longitude}" }, onSelected = { index ->
                            val selected = cities[index]; city = selected.name
                            prefs.edit().putString("weather_city", selected.name).putString("weather_lat", selected.latitude.toString()).putString("weather_lon", selected.longitude.toString()).apply()
                        }, onDismiss = {})
                    }
                }
            }
        }
    }
    @Composable
    private fun PreferenceHeight(id:String,activity:Activity) {
        val prefs=HomeSidePanelSettings.preferences(activity)
        var height by remember(id) { mutableStateOf(SidePanelAppearance.photoHeight(activity,id).toFloat()) }
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp)) {
            Text("卡片高度 ${height.toInt()} dp",color=MiuixTheme.colorScheme.onSurface)
            Slider(value=height,onValueChange={ height=it;prefs.edit().putInt("${id}_height",it.toInt()).apply() },valueRange=80f..500f,modifier=Modifier.fillMaxWidth())
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
