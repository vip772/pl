package t.mma.hooks.api.message;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Read-only fallback for message rows whose source is serialized in lvbuffer. */
public final class WeChatStoredMsgSource {
    private WeChatStoredMsgSource() {}

    public static String resolve(String source, Object buffer) {
        if (source != null && !source.trim().isEmpty()) return source;
        if (buffer == null) return "";
        String raw;
        if (buffer instanceof byte[]) {
            byte[] bytes = (byte[]) buffer;
            if (bytes.length == 0) return "";
            raw = new String(bytes, StandardCharsets.UTF_8);
        } else if (buffer instanceof CharSequence) {
            raw = buffer.toString();
        } else return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("<msgsource");
        if (start < 0) return "";
        int tagEnd = start + "<msgsource".length();
        if (tagEnd >= raw.length() || (raw.charAt(tagEnd) != '>' &&
                !Character.isWhitespace(raw.charAt(tagEnd)))) return "";
        int end = lower.indexOf("</msgsource>", tagEnd);
        return end < 0 ? "" : raw.substring(start, end + "</msgsource>".length());
    }
}
