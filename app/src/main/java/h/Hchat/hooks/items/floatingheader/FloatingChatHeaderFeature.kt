package h.Hchat.hooks.items.floatingheader
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.ui.WeChatLifecycleApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import java.lang.ref.WeakReference
class FloatingChatHeaderFeature : BaseFeature() {
 private var subscription: WeChatLifecycleApi.Subscription? = null
 private val attached = java.util.WeakHashMap<View, WeakReference<ViewGroup>>()
 override fun featureId()=FloatingChatHeaderSettings.ID
 override fun name()="悬浮标题栏"
 override fun onFeatureInit(context: FeatureContext){registerSettingsProvider(FloatingChatHeaderSettingsProvider())}
 override fun onFeatureInstall(context: FeatureContext){ val api=WeChatApis.lifecycle()?:return; subscription=api.subscribe{e->if(e.isResume()) attach(e.activity) else if(e.isDestroy()) detach(e.activity)}; WeChatApis.currentActivity()?.currentActivity()?.let(::attach)}
 override fun onFeatureDestroy(context: FeatureContext){subscription?.unsubscribe();subscription=null;attached.keys.toList().forEach(::restore)}
 private fun attach(a:Activity?){if(a==null||!a.javaClass.name.contains("chatting",true))return;val root=a.window.decorView as? ViewGroup?:return;val h=find(root)?:return;if(attached.containsKey(h))return;val p=h.parent as? ViewGroup?:return;p.removeView(h);val host=FrameLayout(a);host.setPadding(dp(a,12),dp(a,4),dp(a,12),dp(a,4));host.background=GradientDrawable().apply{setColor(0xCCFFFFFF.toInt());cornerRadius=dp(a,24).toFloat()};host.elevation=dp(a,4).toFloat();host.addView(h,FrameLayout.LayoutParams(-1,-2));root.addView(host,FrameLayout.LayoutParams(-1,-2));attached[h]=WeakReference(p)}
 private fun restore(h:View){val p=attached.remove(h)?.get()?:return;val host=h.parent as? ViewGroup?:return;host.removeView(h);p.addView(h,0,h.layoutParams);(host.parent as? ViewGroup)?.removeView(host)}
 private fun detach(a:Activity?){attached.keys.toList().filter{it.context===a}.forEach(::restore)}
 private fun find(r:ViewGroup):View?{var x:View?=null;fun s(v:View){if(x!=null)return;if(v.javaClass.name=="androidx.appcompat.widget.ActionBarContainer"){x=v;return};if(v is ViewGroup)for(i in 0 until v.childCount)s(v.getChildAt(i))};s(r);return x}
 private fun dp(a:Activity,n:Int)=(n*a.resources.displayMetrics.density).toInt()
}
