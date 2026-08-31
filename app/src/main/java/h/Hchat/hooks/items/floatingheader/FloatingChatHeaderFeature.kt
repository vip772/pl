package h.Hchat.hooks.items.floatingheader
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.ui.WeChatLifecycleApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import h.Hchat.utils.HLog
import java.lang.ref.WeakReference
class FloatingChatHeaderFeature:BaseFeature(){
 private var sub:WeChatLifecycleApi.Subscription?=null
 private val states=java.util.WeakHashMap<View,State>()
 private data class State(val parent:WeakReference<ViewGroup>,val index:Int,val params:ViewGroup.LayoutParams)
 override fun featureId()=FloatingChatHeaderSettings.ID
 override fun name()="悬浮标题栏"
 override fun onFeatureInit(context:FeatureContext){registerSettingsProvider(FloatingChatHeaderSettingsProvider())}
 override fun onFeatureInstall(context:FeatureContext){sub=WeChatApis.lifecycle()?.subscribe{e->when{e.isResume()->attach(e.activity);e.isDestroy()->restore(e.activity)}};WeChatApis.currentActivity()?.currentActivity()?.let(::attach)}
 override fun onFeatureDestroy(context:FeatureContext){sub?.unsubscribe();sub=null;states.keys.toList().forEach(::restore)}
 private fun attach(a:Activity?){if(a==null||!a.javaClass.name.contains("chatting",true))return;val root=a.window.decorView as? ViewGroup?:return;val h=find(root)?:return;if(states.containsKey(h))return;val p=h.parent as? ViewGroup?:return;val st=State(WeakReference(p),p.indexOfChild(h),h.layoutParams);p.removeView(h);val host=FrameLayout(a);host.setPadding(dp(a,12),dp(a,4),dp(a,12),dp(a,4));host.background=GradientDrawable().apply{setColor(0xCCFFFFFF.toInt());cornerRadius=dp(a,24).toFloat()};host.elevation=dp(a,4).toFloat();host.addView(h,FrameLayout.LayoutParams(-1,-2));root.addView(host,0,FrameLayout.LayoutParams(-1,-2));states[h]=st}
 private fun restore(a:Activity?){states.keys.toList().filter{a==null||it.rootView.context===a}.forEach{h->val st=states.remove(h)?:return@forEach;val host=h.parent as? ViewGroup?:return@forEach;host.removeView(h);st.parent.get()?.addView(h,st.index.coerceAtMost(st.parent.get()?.childCount?:0),st.params);(host.parent as? ViewGroup)?.removeView(host)}}
 private fun find(r:ViewGroup):View?{var out:View?=null;fun scan(v:View){if(out!=null)return;if(v.javaClass.name=="androidx.appcompat.widget.ActionBarContainer"){out=v;return};if(v is ViewGroup)for(i in 0 until v.childCount)scan(v.getChildAt(i))};scan(r);return out}
 private fun dp(a:Activity,n:Int)=(n*a.resources.displayMetrics.density).toInt()
}
