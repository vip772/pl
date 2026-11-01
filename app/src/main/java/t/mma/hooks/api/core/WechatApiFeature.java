package t.mma.hooks.api.core;
import t.mma.utils.HLog;

import t.mma.hooks.api.contact.*;
import t.mma.hooks.api.conversation.*;
import t.mma.hooks.api.media.WeChatMediaApi;
import t.mma.hooks.api.message.*;
import t.mma.hooks.api.net.WeChatNetworkApi;
import t.mma.hooks.api.net.WeChatNetworkDispatcher;
import t.mma.hooks.api.payment.WeChatTransferApi;
import t.mma.hooks.api.runtime.*;
import t.mma.hooks.api.sns.WeChatSnsApi;
import t.mma.hooks.api.ui.WeChatActivityStartApi;
import t.mma.hooks.api.ui.WeChatChatPageApi;
import t.mma.hooks.api.ui.WeChatCurrentActivityApi;
import t.mma.hooks.api.ui.WeChatLifecycleApi;
import t.mma.hooks.api.ui.WeChatNotifyApi;
import t.mma.hooks.api.ui.WeChatUiApi;
import t.mma.event.Events;
import t.mma.hooks.core.DexInstallScheduler;
import t.mma.hooks.core.Feature;
import t.mma.hooks.core.FeatureContext;
import de.robv.android.xposed.XposedBridge;

/**
 * 公共微信 API 初始化入口。
 */
public class WechatApiFeature implements Feature {
    public static final String ID = "wechat_api";
    private static final String TAG = "[Hchat:WechatApi]";
    private final java.util.ArrayList<AutoCloseable> resources = new java.util.ArrayList<>();
    private final java.util.ArrayList<DexInstallScheduler.Installation> installations = new java.util.ArrayList<>();
    private volatile Generation generation;
    private static final class Generation { volatile boolean closed; }

    private <T extends AutoCloseable> T own(T resource) {
        resources.add(resource);
        return resource;
    }

    private void scheduleOwned(Generation owner, String key, String label,
                               DexInstallScheduler.Stage stage, int priority,
                               java.util.concurrent.Callable<Boolean> task) {
        DexInstallScheduler.Installation ticket = DexInstallScheduler.INSTANCE.installation(key, label, stage, priority);
        installations.add(ticket);
        ticket.schedule(() -> {
            if (owner.closed) return true;
            try { return task.call(); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public String name() {
        return "微信公共API";
    }

    @Override
    public boolean isEnabled(FeatureContext context) {
        return true;
    }

    @Override
    public synchronized void install(FeatureContext context) throws Throwable {
        if (generation != null && !generation.closed) return;
        Generation owner = new Generation();
        generation = owner;
        try {
        WeChatNetworkDispatcher dispatcher = new WeChatNetworkDispatcher(this::log);
        own(dispatcher::destroy);
        WeChatConfigApi configApi = new WeChatConfigApi(context.hostContext(), this::log);
        WeChatNetworkApi networkApi = new WeChatNetworkApi(dispatcher, this::log);
        WeChatDatabaseApi databaseApi = new WeChatDatabaseApi(context.dexFinder(), this::log);
        WeChatAccountApi accountApi = new WeChatAccountApi(
                context.hostContext(),
                context.hostClassLoader(),
                databaseApi,
                this::log);
        WeChatContactApi contactApi = new WeChatContactApi(databaseApi, context.dexFinder(), dispatcher, this::log);
        WeChatNotifyApi notifyApi = new WeChatNotifyApi(context.hostContext(), this::log);
        WeChatUiApi uiApi = new WeChatUiApi(context.hostContext(), notifyApi, this::log);
        WeChatMessageStoreApi messageStoreApi = new WeChatMessageStoreApi(
                databaseApi, accountApi, context.dexFinder(), this::log);
        WeChatMessageApi messageApi = new WeChatMessageApi(
                context.dexFinder(), dispatcher, accountApi, contactApi, messageStoreApi, context.eventBus(), this::log);
        WeChatLocalMessageApi localMessageApi = new WeChatLocalMessageApi(context.dexFinder(), this::log);
        own(localMessageApi);
        localMessageApi.installCreateTimeHook();
        WeChatConversationApi conversationApi = new WeChatConversationApi(
                databaseApi, contactApi, notifyApi, context.dexFinder(), this::log);
        WeChatUserApi userApi = new WeChatUserApi(accountApi, contactApi, this::log);
        WeChatChatroomApi chatroomApi = new WeChatChatroomApi(
                databaseApi, contactApi, context.dexFinder(), dispatcher, this::log);
        WeChatStorageApi storageApi = new WeChatStorageApi(databaseApi, this::log);
        WeChatMessageParseApi messageParseApi = new WeChatMessageParseApi();
        WeChatMessageEventApi messageEventApi = new WeChatMessageEventApi(
                context.dexFinder(), accountApi, messageParseApi, context.eventBus(), this::log);
        own(messageEventApi);
        WeChatPermissionApi permissionApi = new WeChatPermissionApi();
        WeChatDatabaseListenerApi databaseListenerApi = new WeChatDatabaseListenerApi(
                context.dexFinder(), context.hostClassLoader(), this::log);
        own(databaseListenerApi);
        WeChatCurrentActivityApi currentActivityApi = new WeChatCurrentActivityApi(this::log);
        own(currentActivityApi);
        WeChatMediaApi mediaApi = new WeChatMediaApi(
                context.hostContext(), context.dexFinder(), context.hostClassLoader(),
                context.dexKitManager(), currentActivityApi, this::log);
        WeChatActivityStartApi activityStartApi = new WeChatActivityStartApi(this::log);
        own(activityStartApi);
        WeChatMessageChangeApi messageChangeApi = new WeChatMessageChangeApi(
                databaseListenerApi, messageStoreApi, accountApi, this::log);
        own(messageChangeApi);
        WeChatConversationChangeApi conversationChangeApi = new WeChatConversationChangeApi(
                databaseListenerApi, conversationApi, this::log);
        own(conversationChangeApi);
        WeChatContactChangeApi contactChangeApi = new WeChatContactChangeApi(
                databaseListenerApi, contactApi, this::log);
        own(contactChangeApi);
        WeChatChatroomChangeApi chatroomChangeApi = new WeChatChatroomChangeApi(
                databaseListenerApi, chatroomApi, this::log);
        own(chatroomChangeApi);
        WeChatLifecycleApi lifecycleApi = new WeChatLifecycleApi(this::log);
        own(lifecycleApi);
        WeChatDiagnosticsApi diagnosticsApi = new WeChatDiagnosticsApi(
                context.dexFinder(), this::log);
        WeChatTaskApi taskApi = new WeChatTaskApi(context.hostContext(), this::log);
        own(taskApi::shutdown);
        WeChatVersionApi versionApi = new WeChatVersionApi(
                context.hostContext(), context.hostClassLoader(), this::log);
        WeChatMessageObserveApi messageObserveApi = new WeChatMessageObserveApi(
                messageEventApi, messageChangeApi, messageParseApi, accountApi, this::log);
        own(messageObserveApi);
        WeChatChatPageApi chatPageApi = new WeChatChatPageApi(
                context.dexFinder(), currentActivityApi, lifecycleApi, activityStartApi, contactApi, this::log);
        own(chatPageApi);
        WeChatTransferApi transferApi = new WeChatTransferApi(
                context.dexFinder(), dispatcher, this::log);
        WeChatVerifyUserApi verifyUserApi = new WeChatVerifyUserApi(
                context.dexFinder(), dispatcher, this::log);
        WeChatSnsApi snsApi = new WeChatSnsApi(
                context.hostContext(), context.dexFinder(), context.hostClassLoader(),
                context.dexKitManager(), networkApi, accountApi, contactApi, context, this::log);
        own(snsApi);
        // 网络队列 Hook 延后到 wechat_api:warmup，避免在微信启动网络状态未就绪时扫描并挂钩候选类。
        messageEventApi.installAddMsgHook();
        installApiHook(owner, "databaseChanges", databaseListenerApi::install);
        installApiHook(owner, "messageChanges", messageChangeApi::install);
        installApiHook(owner, "conversationChanges", conversationChangeApi::install);
        installApiHook(owner, "contactChanges", contactChangeApi::install);
        installApiHook(owner, "chatroomChanges", chatroomChangeApi::install);
        installApiHook(owner, "currentActivity", currentActivityApi::install);
        installApiHook(owner, "activityStart", activityStartApi::install);
        installApiHook(owner, "lifecycle", lifecycleApi::install);
        installApiHook(owner, "messageObserve", messageObserveApi::install);
        installApiHook(owner, "chatPage", chatPageApi::install);
        WeChatApis.init(messageApi, databaseApi, accountApi, contactApi,
                messageStoreApi, conversationApi, notifyApi, configApi, networkApi, userApi,
                chatroomApi, storageApi, messageParseApi, messageEventApi,
                localMessageApi, uiApi, mediaApi, permissionApi, databaseListenerApi,
                currentActivityApi, activityStartApi, messageChangeApi,
                conversationChangeApi, contactChangeApi, chatroomChangeApi,
                lifecycleApi, diagnosticsApi, taskApi, messageObserveApi, chatPageApi, versionApi,
                transferApi, verifyUserApi, snsApi);
        scheduleOwned(owner, "wechat_api:warmup", "微信公共API预热",
                DexInstallScheduler.Stage.BRIDGE, 1000, () -> {
            try {
                context.dexFinder().resolveAll();
                if (owner.closed) return true;
                networkApi.installNetworkHook(context.dexFinder());
                if (owner.closed) return true;
                installApiHook(owner, "snsInteractionWarmup", snsApi::warmupInteraction);
                installApiHook(owner, "snsCachedPostsWarmup", snsApi::warmupCachedPosts);
                installApiHook(owner, "snsPostObserverWarmup", snsApi::installPostObserver);
                messageEventApi.installAddMsgHook();
                installApiHook(owner, "databaseChangesWarmup", databaseListenerApi::install);
                installApiHook(owner, "messageChangesWarmup", messageChangeApi::install);
                installApiHook(owner, "conversationChangesWarmup", conversationChangeApi::install);
                installApiHook(owner, "contactChangesWarmup", contactChangeApi::install);
                installApiHook(owner, "chatroomChangesWarmup", chatroomChangeApi::install);
                installApiHook(owner, "messageObserveWarmup", messageObserveApi::install);
                installApiHook(owner, "chatPageWarmup", chatPageApi::install);
            } catch (Throwable e) {
                log("DexKit 预热失败: " + e.getMessage());
            } finally {
                try {
                    if (owner.closed) return true;
                    DexInstallScheduler.markDexWarmupReady();
                    context.eventBus().post(new Events.DexReady());
                } catch (Throwable e) {
                    log("DexReady 发布失败: " + e.getMessage());
                }
            }
            return true;
        });
        scheduleOwned(owner,
                "wechat_api:database_changes",
                "数据库变更监听补装",
                DexInstallScheduler.Stage.WARMUP,
                -1000,
                () -> {
                    context.dexFinder().resolveDatabaseApi();
                if (owner.closed) return true;
                    databaseListenerApi.install();
                    messageChangeApi.install();
                    conversationChangeApi.install();
                    contactChangeApi.install();
                    chatroomChangeApi.install();
                    return databaseListenerApi.isOperational() && messageChangeApi.isInstalled();
                });
        scheduleOwned(owner,
                "wechat_api:private_conversation_mute",
                "私聊免打扰API补定位",
                DexInstallScheduler.Stage.WARMUP,
                0,
                () -> {
                    context.dexFinder().resolveConversationMuteApi();
                if (owner.closed) return true;
                    return context.dexFinder().isPrivateConversationMuteApiReady();
                });
        } catch (Throwable failure) {
            onDestroy(context);
            throw failure;
        }
    }

    @Override
    public synchronized void onDestroy(FeatureContext context) {
        Generation owner = generation;
        if (owner == null || owner.closed) return;
        owner.closed = true;
        for (DexInstallScheduler.Installation ticket : installations) {
            try { ticket.close(); } catch (Throwable ignored) { }
        }
        installations.clear();
        for (int i = resources.size() - 1; i >= 0; i--) {
            try { resources.get(i).close(); } catch (Throwable ignored) { }
        }
        resources.clear();
        WeChatApis.clear();
    }

    private void log(String message) {
        if (message == null) return;
        String line = TAG + " " + message;
        // 公共 API 的全部阶段日志进入 Hchat 运行日志；重要日志另外写入 LSPosed。
        // 这样可以还原一次 API 调用经过了哪些阶段，而不是只看到最终失败。
        HLog.d(line);
        if (isImportantLog(message)) {
            XposedBridge.log(line);
        }
    }

    private void installApiHook(Generation owner, String name, Runnable runnable) {
        if (owner.closed) return;
        try {
            runnable.run();
        } catch (Throwable e) {
            log(name + " API安装失败: " + e.getMessage());
        }
    }

    private boolean isImportantLog(String message) {
        if (message == null) return false;
        return message.contains("失败")
                || message.contains("异常")
                || message.contains("未找到")
                || message.contains("为空")
                || message.contains("不可用")
                || message.contains("未就绪")
                || message.contains("未解析")
                || message.contains("缺失")
                || message.contains("无合适")
                || message.contains("ERROR")
                || message.contains("error");
    }
}
