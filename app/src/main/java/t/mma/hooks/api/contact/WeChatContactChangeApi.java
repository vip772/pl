package t.mma.hooks.api.contact;

import android.content.ContentValues;
import android.text.TextUtils;

import t.mma.hooks.api.model.DatabaseChange;
import t.mma.hooks.api.model.WeChatContact;
import t.mma.hooks.api.runtime.WeChatDatabaseListenerApi;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 联系人相关表变更监听 API。
 */
public final class WeChatContactChangeApi implements AutoCloseable {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onContactChanged(ContactChange change);
    }

    public static final class ContactChange {
        public final DatabaseChange databaseChange;
        public final WeChatContact contact;

        private ContactChange(DatabaseChange databaseChange, WeChatContact contact) {
            this.databaseChange = databaseChange;
            this.contact = contact;
        }

        public String wxId() {
            if (contact != null) return contact.wxId;
            return str(databaseChange != null ? databaseChange.values : null, "username");
        }

        public String operation() {
            return databaseChange != null ? databaseChange.operation : "";
        }

        public boolean isAvatarChange() {
            return databaseChange != null && "img_flag".equalsIgnoreCase(databaseChange.table);
        }
    }

    public final class Subscription {
        private final Listener listener;
        private volatile boolean active = true;

        private Subscription(Listener listener) {
            this.listener = listener;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            listeners.remove(listener);
        }

        public boolean isActive() {
            return active && !closed;
        }
    }

    private final WeChatDatabaseListenerApi databaseListenerApi;
    private final WeChatContactApi contactApi;
    private final Logger logger;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean installed;
    private volatile boolean closed;
    private WeChatDatabaseListenerApi.Subscription upstream;

    public WeChatContactChangeApi(WeChatDatabaseListenerApi databaseListenerApi,
                                  WeChatContactApi contactApi,
                                  Logger logger) {
        this.databaseListenerApi = databaseListenerApi;
        this.contactApi = contactApi;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return databaseListenerApi != null && databaseListenerApi.isAvailable();
    }

    public synchronized Subscription subscribe(Listener listener) {
        if (closed || listener == null) return null;
        listeners.addIfAbsent(listener);
        return new Subscription(listener);
    }

    public void unsubscribe(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    /** Only the public API generation owner closes shared services. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        installed = false;
        WeChatDatabaseListenerApi.Subscription subscription = upstream;
        upstream = null;
        listeners.clear();
        if (subscription != null) subscription.unsubscribe();
    }

    public synchronized void install() {
        if (closed || installed || databaseListenerApi == null) return;
        upstream = databaseListenerApi.subscribe(this::onDatabaseChanged);
        installed = true;
        log("联系人变更监听已安装");
    }

    private void onDatabaseChanged(DatabaseChange change) {
        if (closed) return;
        if (change == null || (!"rcontact".equalsIgnoreCase(change.table)
                && !"img_flag".equalsIgnoreCase(change.table))) {
            return;
        }
        WeChatContact contact = resolveContact(change);
        ContactChange event = new ContactChange(change, contact);
        for (Listener listener : listeners) {
            if (closed) return;
            try {
                listener.onContactChanged(event);
            } catch (Throwable e) {
                log("联系人变更回调失败: " + e.getMessage());
            }
        }
    }

    private WeChatContact resolveContact(DatabaseChange change) {
        String username = str(change.values, "username");
        if (!TextUtils.isEmpty(username) && contactApi != null) {
            WeChatContact stored = contactApi.getContact(username);
            if (stored != null) return stored;
        }
        if (!"rcontact".equalsIgnoreCase(change.table) || change.values == null) return null;
        return new WeChatContact(
                username,
                str(change.values, "nickname"),
                str(change.values, "alias"),
                str(change.values, "conRemark"),
                "",
                "",
                str(change.values, "encryptUsername"),
                str(change.values, "province"),
                str(change.values, "city"),
                intValue(change.values, "sex"),
                intValue(change.values, "type"));
    }

    private static String str(ContentValues values, String key) {
        if (values == null || TextUtils.isEmpty(key) || !values.containsKey(key)) return "";
        Object value = values.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private static int intValue(ContentValues values, String key) {
        if (values == null || TextUtils.isEmpty(key) || !values.containsKey(key)) return 0;
        try {
            Long value = values.getAsLong(key);
            return value != null ? value.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatContactChangeApi] " + message);
    }
}
