# DexKit
-keep class org.luckypray.dexkit.DexKitBridge { *; }

# BeanShell 用户脚本引擎
-keep class bsh.** { *; }

# Android Xml API 返回系统 XmlPullParser 实现，接口 ABI 不能重命名或收窄
-keep class org.xmlpull.v1.** { *; }

-keep class t.mma.hooks.items.script.ScriptPluginRuntime { *; }
-keep class t.mma.hooks.items.script.ScriptPluginBridge { *; }
-keep class t.mma.hooks.items.script.ScriptFloatingGlassBarHandle { *; }
-keep class t.mma.hooks.items.script.ScriptDexKitBridge { *; }
-keep class t.mma.hooks.items.script.ScriptWaBridge { *; }
-keep class t.mma.hooks.items.script.ScriptAudioBridge { *; }
-keep class t.mma.hooks.items.script.ScriptMessageBean { *; }
-keep class t.mma.hooks.items.script.ScriptQuoteMsgBean { *; }
-keep class t.mma.hooks.items.script.ScriptPluginRuntime$SendResult { *; }
-keep class t.mma.hooks.items.protobuf.ProtobufPacketRuntime$Packet { *; }
-keep class t.mma.hooks.api.model.ContactLabelBean { *; }
-keep class t.mma.hooks.api.model.WeChatContact { *; }
-keep class t.mma.hooks.api.model.WeChatChatroom { *; }
-keep class t.mma.hooks.api.model.WeChatMessage { *; }
-keep class t.mma.hooks.api.model.WeChatQuoteMsg { *; }
-keep class t.mma.hooks.api.model.WeChatImageMsg { *; }
-keep class t.mma.hooks.api.model.WeChatVideoMsg { *; }
-keep class t.mma.hooks.api.model.WeChatFileMsg { *; }
-keep class t.mma.hooks.api.model.WeChatTransferMsg { *; }
-keep class t.mma.hooks.api.model.WeChatPatMsg { *; }
-keep class t.mma.hooks.api.model.WeChatSnsMedia { *; }
-keep class t.mma.hooks.api.model.WeChatSnsPost { *; }
-keep class t.mma.hooks.api.model.WeChatSnsLivePhoto { *; }
-keep class t.mma.hooks.api.model.WeChatSnsPrepareResult { *; }
-keep class t.mma.hooks.api.core.WeChatApis { *; }
-keep class t.mma.hooks.api.runtime.WeChatDatabaseApi { *; }
-keep class t.mma.dexkit.DexBridgeHolder { *; }
-keep class t.mma.dexkit.DexFinder { *; }
-keep class t.mma.utils.KavaReflector { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.alibaba.fastjson2.** { *; }
-keep class me.hd.wauxv.plugin.api.callback.** { *; }
-keep class me.hd.wauxv.data.bean.** { *; }
-keep class me.hd.wauxv.data.bean.info.** { *; }
-keep class me.yun.silk.** { *; }
-keep class me.yun.silk.utils.** { *; }

# Xposed
-keep class de.robv.android.xposed.** { *; }
-keep class t.mma.ModuleEntry { *; }
-keep class t.mma.crash.NativeCrashBridge { *; }
-keep class t.mma.crash.CrashExitInfoApi30 { *; }

# 保留反射访问的微信内部类字段名
-keepclassmembers class * {
    java.lang.String e;
    java.lang.String f;
    java.lang.String h;
    java.lang.String i;
    java.lang.String m;
    java.lang.String d;
}
# API 102 modern Xposed entry metadata.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class t.mma.Api102ModuleEntry {
    public <init>();
}
