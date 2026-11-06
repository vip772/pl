import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import t.mma.hooks.api.contact.WeChatChatroomApi;

/** Exercises production protobuf parsing, not a duplicate parser. */
public final class NativeJoinTimeTest {
    static void varint(ByteArrayOutputStream out, long value) {
        while ((value & ~127L) != 0) { out.write((int)(value & 127) | 128); value >>>= 7; }
        out.write((int)value);
    }
    static byte[] member(long scene, Long join, Long unknown) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(40); varint(out, scene); // field 5, addChatRoomScene
        if (join != null) { out.write(48); varint(out, join); }
        if (unknown != null) { out.write(56); varint(out, unknown); }
        return out.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        WeChatChatroomApi api = new WeChatChatroomApi(null, null, null);
        Method parse = WeChatChatroomApi.class.getDeclaredMethod("parseRoomDataMember", byte[].class);
        parse.setAccessible(true);
        Object sample = parse.invoke(api, (Object)member(1700000000L, 1700000123L, 1600000000L));
        Field time = sample.getClass().getDeclaredField("joinTimeMillis"); time.setAccessible(true);
        check(time.getLong(sample) == 1700000123000L, "native field 6 must win over scene/unknown");
        check(time.getLong(parse.invoke(api, (Object)member(1700000000L, null, 1700000000L))) == 0, "other fields are not join times");
        for (long invalid : new long[]{0, 123, Long.MAX_VALUE, System.currentTimeMillis()/1000 + 172800}) {
            check(time.getLong(parse.invoke(api, (Object)member(0, invalid, null))) == 0, "invalid native timestamp");
        }
        check(time.getLong(parse.invoke(api, (Object)new byte[]{48, (byte)128})) == 0, "truncated protobuf must not fabricate time");
        System.out.println("PASS native roomdata production parser: field 6, seconds conversion, scene/unknown exclusion, invalid/future/truncated data");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
