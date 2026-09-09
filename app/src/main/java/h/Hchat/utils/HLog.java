package h.Hchat.utils;

import de.robv.android.xposed.XposedBridge;

/**
 * LSPosed treats Throwable logs as error logs. Use this only for real failures.
 */
public final class HLog {
    private HLog() {
    }

    public static void initialize(android.content.Context context) {
        RuntimeLogStore.initialize(context);
    }
    public static void e(String message) {
        RuntimeLogStore.append("ERROR", message, null);
        XposedBridge.log(new RuntimeException(message));
    }
    public static void e(String message, Throwable throwable) {
        if (throwable == null) {
            e(message);
            return;
        }
        RuntimeLogStore.append("ERROR", message, throwable);
        XposedBridge.log(new RuntimeException(message, throwable));
    }
}
