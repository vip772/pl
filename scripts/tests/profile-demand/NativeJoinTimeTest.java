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
        Field failed = sample.getClass().getDeclaredField("parseFailed"); failed.setAccessible(true);
        check(!failed.getBoolean(sample), "valid timestamp parses successfully");
        Object absent = parse.invoke(api, (Object)member(0, null, null));
        check(time.getLong(absent) == 0 && !failed.getBoolean(absent), "absent metadata is not a parse error");
        Object zero = parse.invoke(api, (Object)member(0, 0L, null));
        check(time.getLong(zero) == 0 && !failed.getBoolean(zero), "zero timestamp remains unavailable");
        Object truncated = parse.invoke(api, (Object)new byte[]{48, (byte)128});
        check(failed.getBoolean(truncated), "truncated value is a parse failure, not absent metadata");
        check(time.getLong(sample) == 1700000123000L, "native field 6 must win over scene/unknown");
        check(time.getLong(parse.invoke(api, (Object)member(1700000000L, null, 1700000000L))) == 0, "other fields are not join times");
        for (long invalid : new long[]{0, 123, Long.MAX_VALUE, System.currentTimeMillis()/1000 + 172800}) {
            check(time.getLong(parse.invoke(api, (Object)member(0, invalid, null))) == 0, "invalid native timestamp");
        }
        check(time.getLong(parse.invoke(api, (Object)new byte[]{48, (byte)128})) == 0, "truncated protobuf must not fabricate time");
        Method join = WeChatChatroomApi.class.getDeclaredMethod("isMemberJoinSystemMessage", int.class, String.class, String.class);
        join.setAccessible(true);
        check((Boolean)join.invoke(api, 10000, "wxid_inviter 邀请 wxid_joiner", "wxid_inviter"), "legacy inviter match");
        check((Boolean)join.invoke(api, 10000, "wxid_inviter 邀请 wxid_joiner", "wxid_joiner"), "legacy invitee match");
        check((Boolean)join.invoke(api, 10002, "<sysmsgtemplate><username>wxid_joiner</username></sysmsgtemplate>", "wxid_joiner"), "legacy XML fallback");
        check((Boolean)join.invoke(api, 922746929, "wxid_joiner invite", "wxid_joiner"), "legacy additional message type");
        check((Boolean)join.invoke(api, 10000, "邀请者：wxid_inviter 被邀请者：wxid_joiner 累计邀请：7", "wxid_joiner"), "local invitation detail compatibility");
        check(!(Boolean)join.invoke(api, 1, "wxid_joiner 加入了群聊", "wxid_joiner"), "ordinary text excluded");
        check(!(Boolean)join.invoke(api, 10000, "wxid_other 加入了群聊", "wxid_joiner"), "missing identity excluded");
        check(!(Boolean)join.invoke(api, 10000, "wxid_joiner 修改了群名", "wxid_joiner"), "unrelated event excluded");
        check(!api.isJoinTimeResultComplete(api.getMemberJoinTimes("group@chatroom")), "database unavailable is incomplete, not confirmed empty");
        System.out.println("PASS native roomdata production parser: field 6, seconds conversion, scene/unknown exclusion, invalid/future/truncated data");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
