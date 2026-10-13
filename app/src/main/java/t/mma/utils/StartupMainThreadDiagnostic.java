package t.mma.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded startup heartbeat. Stack capture and formatting run off the UI thread. */
public final class StartupMainThreadDiagnostic {
    private static final AtomicBoolean started = new AtomicBoolean();
    private StartupMainThreadDiagnostic() {}
    public static void start() {
        if (!started.compareAndSet(false, true)) return;
        final Handler main = new Handler(Looper.getMainLooper());
        final Thread ui = Looper.getMainLooper().getThread();
        final AtomicLong acknowledged = new AtomicLong();
        new Thread(() -> {
            final long end = SystemClock.uptimeMillis() + 180000L;
            int captures = 0;
            long sequence = 0;
            HLog.i("[Hchat:StartupMainDiagnostic:v1] armed pid=" + Process.myPid() + " windowMs=180000");
            try {
                while (SystemClock.uptimeMillis() < end) {
                    final long token = ++sequence;
                    final long sent = SystemClock.uptimeMillis();
                    if (!main.post(() -> acknowledged.set(token))) break;
                    long nextCapture = sent + 500L;
                    boolean stalled = false;
                    while (acknowledged.get() < token && SystemClock.uptimeMillis() < end) {
                        Thread.sleep(100L);
                        long now = SystemClock.uptimeMillis();
                        if (acknowledged.get() < token && now >= nextCapture && captures < 16) {
                            stalled = true;
                            captures++;
                            nextCapture = now + 1000L;
                            StringBuilder trace = new StringBuilder("[Hchat:StartupMainDiagnostic:v1] blockedMs=")
                                .append(now - sent).append(" pid=").append(Process.myPid())
                                .append(" state=").append(ui.getState());
                            StackTraceElement[] stack = ui.getStackTrace();
                            trace.append(" mainFrames=").append(stack.length);
                            for (int i = 0; i < Math.min(stack.length, 128); i++) trace.append("\n  at ").append(stack[i]);
                            // Retain callers if repeated iterator frames fill the head.
                            if (stack.length > 128) {
                                int tailStart = Math.max(128, stack.length - 32);
                                trace.append("\n  ... omittedFrames=").append(tailStart - 128);
                                for (int i = tailStart; i < stack.length; i++) trace.append("\n  at ").append(stack[i]);
                            }
                            java.util.Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
                            int included = 0;
                            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : threads.entrySet()) {
                                Thread worker = entry.getKey();
                                if (worker == ui || worker == Thread.currentThread() ||
                                    !worker.getName().startsWith("Hchat") || included >= 8) continue;
                                included++;
                                trace.append("\nworker=").append(worker.getName()).append(" state=").append(worker.getState());
                                StackTraceElement[] frames = entry.getValue();
                                for (int i = 0; i < Math.min(frames.length, 20); i++) trace.append("\n  at ").append(frames[i]);
                            }
                            Runtime runtime = Runtime.getRuntime();
                            trace.append("\nheapUsed=").append(runtime.totalMemory() - runtime.freeMemory())
                                .append(" heapMax=").append(runtime.maxMemory());
                            HLog.i(trace.toString());
                        }
                    }
                    if (stalled && acknowledged.get() >= token) {
                        HLog.i("[Hchat:StartupMainDiagnostic:v1] recoveredMs=" + (SystemClock.uptimeMillis() - sent));
                    }
                    if (captures >= 16) break;
                    Thread.sleep(250L);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable failure) {
                HLog.i("[Hchat:StartupMainDiagnostic:v1] stopped=" + failure.getClass().getSimpleName());
            }
            HLog.i("[Hchat:StartupMainDiagnostic:v1] finished captures=" + captures);
        }, "Hchat-StartupWatch").start();
    }
}
