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
        Field present = sample.getClass().getDeclaredField("joinTimePresent"); present.setAccessible(true);
        Field failed = sample.getClass().getDeclaredField("parseFailed"); failed.setAccessible(true);
        Field raw = sample.getClass().getDeclaredField("rawJoinTime"); raw.setAccessible(true);
        check(present.getBoolean(sample) && raw.getLong(sample) == 1700000123L && !failed.getBoolean(sample), "diagnostic valid timestamp classification");
        Object absent = parse.invoke(api, (Object)member(0, null, null));
        check(!present.getBoolean(absent) && !failed.getBoolean(absent), "absent is distinct from zero and parse error");
        Object zero = parse.invoke(api, (Object)member(0, 0L, null));
        check(present.getBoolean(zero) && raw.getLong(zero) == 0 && !failed.getBoolean(zero), "explicit zero classification");
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
        check(!(Boolean)join.invoke(api, 10000, "wxid_inviter 邀请 wxid_joiner", "wxid_inviter"), "inviter action must not become inviter join date");
        check(!(Boolean)join.invoke(api, 10000, "wxid_joiner 邀请你加入群聊", "wxid_joiner"), "invitation is not a completed join");
        check(!(Boolean)join.invoke(api, 10002, "<sysmsgtemplate><username>wxid_joiner</username></sysmsgtemplate>", "wxid_joiner"), "template identity without a verified joiner role is insufficient");
        check(!(Boolean)join.invoke(api, 922746929, "wxid_joiner 加入了群聊", "wxid_joiner"), "app message cannot assert a join event");
        check(!(Boolean)join.invoke(api, 10000, "wxid_joiner_extra 加入了群聊", "wxid_joiner"), "member ID substring is not an exact identity");
        check((Boolean)join.invoke(api, 10000, "“wxid_joiner”加入了群聊。", "wxid_joiner"), "explicit completed join subject is accepted");
        check(!(Boolean)join.invoke(api, 10000, "wxid_joiner 加入了群聊 需要管理员批准", "wxid_joiner"), "trailing qualification cannot assert a completed join");
        check(!(Boolean)join.invoke(api, 10000, "wxid_inviter 邀请 “wxid_joiner”加入了群聊。", "wxid_inviter"), "invitation actor is excluded from completed join subject");
        Method shape = WeChatChatroomApi.class.getDeclaredMethod("historyShape", String.class);
        shape.setAccessible(true);
        String structure = (String) shape.invoke(null, "<sysmsg><link name='inviter'><username><![CDATA[private_member_id]]></username><nickname>private_name</nickname></link></sysmsg>");
        check(structure.contains("roleCount=1") && structure.contains("userCount=1"), "history diagnostic retains structural roles and identities");
        check(!structure.contains("private_member_id") && !structure.contains("private_name") && !structure.contains("inviter"), "history diagnostic excludes raw names, IDs and role attribute values");
        check(!api.isJoinTimeResultComplete(api.getMemberJoinTimes("group@chatroom")), "database unavailable is incomplete, not confirmed empty");
        Class<?> probes = Class.forName("t.mma.hooks.items.profileid.JoinTimeNativeDiagnostics");
        Object instance = probes.getField("INSTANCE").get(null);
        Method transitions = probes.getDeclaredMethod("transitions", java.util.Map.class, java.util.Map.class, java.util.Map.class);
        transitions.setAccessible(true);
        String changes = (String) transitions.invoke(instance,
                java.util.Map.of("cleared", 1700000000L, "removed", 1700000000L),
                java.util.Map.of("cleared", 0L, "copied", 1700000100L),
                java.util.Map.of("cleared", 1700000000L, "copied", 1700000100L));
        check(changes.contains("cleared=1") && changes.contains("removed=1") && changes.contains("incomingPositiveCopied=1") && changes.contains("incomingPositiveNotCopied=1"), "production native transition classifier distinguishes clearing, omission and copying");
        System.out.println("PASS production diagnostic structure/privacy, incomplete query status and per-member native transitions");
        System.out.println("PASS native roomdata production parser: field 6, seconds conversion, scene/unknown exclusion, invalid/future/truncated data");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
