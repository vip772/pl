package t.mma.hooks.api.contact;

import android.text.TextUtils;
import android.database.Cursor;

import t.mma.dexkit.DexFinder;
import t.mma.hooks.api.model.WeChatChatroom;
import t.mma.hooks.api.model.WeChatMessageTypes;
import t.mma.hooks.api.model.WeChatContact;
import t.mma.hooks.api.net.WeChatNetworkDispatcher;
import t.mma.hooks.api.runtime.WeChatDatabaseApi;
import t.mma.utils.KavaReflector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信群聊 API。
 */
public final class WeChatChatroomApi {
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;
    private static final int ROOM_FLAG_ADMIN = 0x800;

    public interface Logger {
        void log(String message);
    }

    private final WeChatDatabaseApi databaseApi;
    private final WeChatContactApi contactApi;
    private final DexFinder dexFinder;
    private final WeChatNetworkDispatcher networkDispatcher;
    private final Logger logger;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();
    private volatile boolean networkHookInstalled;

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             Logger logger) {
        this(databaseApi, contactApi, null, null, logger);
    }

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             DexFinder dexFinder,
                             WeChatNetworkDispatcher networkDispatcher,
                             Logger logger) {
        this.databaseApi = databaseApi;
        this.contactApi = contactApi;
        this.dexFinder = dexFinder;
        this.networkDispatcher = networkDispatcher;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return databaseApi != null && databaseApi.isAvailable()
                && contactApi != null && contactApi.isAvailable();
    }

    public WeChatChatroom getChatroom(String chatroomId) {
        if (!isGroup(chatroomId) || databaseApi == null) return null;
        try {
            List<Map<String, Object>> rows = databaseApi.query(
                    "SELECT chatroomname, memberlist, displayname, roomowner "
                            + "FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{chatroomId});
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            return new WeChatChatroom(
                    str(row, "chatroomname"),
                    getChatroomName(chatroomId),
                    str(row, "roomowner"),
                    splitMembers(str(row, "memberlist")),
                    str(row, "displayname"));
        } catch (Throwable e) {
            log("读取群聊失败: " + chatroomId + " " + e.getMessage());
            return null;
        }
    }

    public List<WeChatChatroom> getChatrooms() {
        List<WeChatChatroom> result = new ArrayList<>();
        if (contactApi == null) return result;
        for (WeChatContact contact : contactApi.getGroups()) {
            WeChatChatroom room = getChatroom(contact.wxId);
            if (room != null) {
                result.add(room);
            } else {
                result.add(new WeChatChatroom(
                        contact.wxId, contact.displayName(), "", new ArrayList<>(), ""));
            }
        }
        return result;
    }

    public String getChatroomName(String chatroomId) {
        if (TextUtils.isEmpty(chatroomId)) return "";
        String name = contactApi != null ? contactApi.getDisplayName(chatroomId) : "";
        return !TextUtils.isEmpty(name) ? name : chatroomId;
    }

    public String getOwner(String chatroomId) {
        if (TextUtils.isEmpty(chatroomId) || databaseApi == null) return "";
        try {
            String owner = databaseApi.queryFirstString(
                    "SELECT roomowner FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{chatroomId},
                    "roomowner");
            if (!TextUtils.isEmpty(owner)) return owner.trim();
        } catch (Throwable e) {
            log("读取群主失败: " + chatroomId + " " + e.getMessage());
        }
        WeChatChatroom room = getChatroom(chatroomId);
        return room != null ? room.owner : "";
    }

    public List<String> getMemberIds(String chatroomId) {
        if (contactApi != null) return contactApi.getGroupMemberIds(chatroomId);
        WeChatChatroom room = getChatroom(chatroomId);
        return room != null ? room.memberIds : new ArrayList<>();
    }

    public int getMemberCount(String chatroomId) {
        return getMemberIds(chatroomId).size();
    }

    public boolean isMember(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(memberWxId)) return false;
        for (String member : getMemberIds(chatroomId)) {
            if (memberWxId.equals(member)) return true;
        }
        return false;
    }

    public String getMemberDisplayName(String chatroomId, String memberWxId) {
        if (contactApi == null) return !TextUtils.isEmpty(memberWxId) ? memberWxId : "";
        return contactApi.getGroupMemberDisplayName(chatroomId, memberWxId);
    }
    public String getRoomDisplayName(String chatroomId, String memberWxId) {
        if (contactApi == null) return memberWxId != null ? memberWxId : "";
        return contactApi.getGroupMemberRoomDisplayName(chatroomId, memberWxId);
    }

    public int getMemberRole(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(chatroomId) || TextUtils.isEmpty(memberWxId)) return ROLE_MEMBER;
        String owner = getOwner(chatroomId);
        if (!TextUtils.isEmpty(owner) && owner.equals(memberWxId)) return ROLE_OWNER;
        RoomDataMember member = getRoomDataMembers(chatroomId).get(memberWxId);
        return member != null && (member.roomFlag & ROOM_FLAG_ADMIN) != 0 ? ROLE_ADMIN : ROLE_MEMBER;
    }

    public String getMemberRoleName(String chatroomId, String memberWxId) {
        int role = getMemberRole(chatroomId, memberWxId);
        if (role == ROLE_OWNER) return "群主";
        if (role == ROLE_ADMIN) return "管理员";
        return "群员";
    }
    public String getMemberInviter(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(chatroomId) || TextUtils.isEmpty(memberWxId)) return "";
        RoomDataMember member = getRoomDataMembers(chatroomId).get(memberWxId);
        if (member == null || TextUtils.isEmpty(member.inviterWxId)) return "";
        String inviter = member.inviterWxId.trim();
        return memberWxId.equals(inviter) ? "" : inviter;
    }

    /**
     * Reads all known member join times for a group in one background-friendly pass.
     * The result prefers native roomdata.joinTime; history fills only members missing native time.
     */
    private static final class JoinTimeResult extends LinkedHashMap<String, Long> {
        boolean complete = true;
    }
    public boolean isJoinTimeResultComplete(Map<String, Long> result) {
        return result instanceof JoinTimeResult && ((JoinTimeResult) result).complete;
    }
    public void logPersistedJoinTimes(String groupId, long eventId, String expectedHash) {
        NativeJoinDiagnostics diagnostic = new NativeJoinDiagnostics();
        getRoomDataMembers(groupId, diagnostic);
        log("JOIN_DIAG persistedRead event=" + eventId + " group=" + groupId + " expectedHash=" + expectedHash
                + " hashMatches=" + expectedHash.equals(diagnostic.blobHash) + " " + diagnostic);
    }
    public Map<String, Long> getMemberJoinTimes(String chatroomId) {
        JoinTimeResult result = new JoinTimeResult();
        if (TextUtils.isEmpty(chatroomId) || databaseApi == null) {
            result.complete = false;
            log("JOIN_DIAG query state=input-unavailable");
            return result;
        }
        // WeChat 8.0.78 so.b: field 5 = addChatRoomScene, field 6 = joinTime (seconds).
        // Prefer persisted member metadata; local system-message history may be incomplete.
        NativeJoinDiagnostics nativeDiagnostics = new NativeJoinDiagnostics();
        Map<String, RoomDataMember> roomMembers = getRoomDataMembers(chatroomId, nativeDiagnostics);
        log("JOIN_DIAG native group=" + chatroomId + " " + nativeDiagnostics);
        if (!"complete".equals(nativeDiagnostics.state) || nativeDiagnostics.parseErrors > 0) result.complete = false;
        for (Map.Entry<String, RoomDataMember> entry : roomMembers.entrySet()) {
            if (entry.getValue().joinTimeMillis > 0L) result.put(entry.getKey(), entry.getValue().joinTimeMillis);
        }
        int nativeHits = result.size();
        long queryStarted = android.os.SystemClock.elapsedRealtime();
        java.util.Set<String> members = new java.util.LinkedHashSet<>(getMemberIds(chatroomId));
        WeChatChatroom room = getChatroom(chatroomId);
        if (room != null) members.addAll(room.memberIds);
        members.addAll(roomMembers.keySet());
        if (members.isEmpty()) {
            result.complete = false;
            log("群成员进群时间暂不可查: group=" + chatroomId + " reason=member-list-empty");
            return result;
        }
        java.util.Set<String> missingMembers = new java.util.LinkedHashSet<>(members);
        missingMembers.removeAll(result.keySet());
        if (missingMembers.isEmpty()) {
            log("群成员进群时间索引完成: group=" + chatroomId + " tables=0 members=" + members.size()
                    + " hits=" + result.size() + " roomdataHits=" + nativeHits + " messageHits=0");
            return result;
        }
        try {
            List<String> tables = new ArrayList<>();
            String mapped = databaseApi.messageTableForTalker(chatroomId);
            if (!TextUtils.isEmpty(mapped)) tables.add(mapped);
            for (String table : databaseApi.messageTables(true)) {
                if (!tables.contains(table)) tables.add(table);
            }
            log("JOIN_DIAG discovery group=" + chatroomId + " mapped=" + mapped + " tables=" + tables.size());
            if (tables.isEmpty()) result.complete = false;
            long totalRows = 0L, totalIdentityRows = 0L, totalAcceptedRows = 0L;
            for (String table : tables) {
                long rowsRead = 0L, identityRows = 0L, acceptedRows = 0L, invalidTimeRows = 0L;
                String quoted = databaseApi.quoteTable(table);
                if (TextUtils.isEmpty(quoted)) {
                    result.complete = false;
                    log("JOIN_DIAG historyTable group=" + chatroomId + " table=" + table + " state=invalid-table");
                    continue;
                }
                java.util.Set<String> structuralShapes = new java.util.HashSet<>();
                int shapeOmittedRows = 0;
                logHistoryTypeInventory(chatroomId, table, quoted);
                // Stream all matching records: a fixed oldest-20000 limit permanently
                // loses recent joins in active groups, while materializing them wastes memory.
                try (Cursor cursor = databaseApi.rawQuery(
                        "SELECT type, createTime, content FROM " + quoted
                                + " WHERE talker=? AND (type=? OR type=? OR type=? OR type=?"
                                + " OR type=? OR type=? OR type=? OR type=? OR type=?)"
                                + " ORDER BY createTime ASC",
                        new String[]{chatroomId, "10000", "10002", "64", "603979825",
                                "268445456", "268445458", "285222674", "889192497", "922746929"})) {
                    if (cursor == null) throw new IllegalStateException("join-time cursor unavailable: " + table);
                    int contentColumn = cursor.getColumnIndexOrThrow("content");
                    int typeColumn = cursor.getColumnIndexOrThrow("type");
                    int timeColumn = cursor.getColumnIndexOrThrow("createTime");
                    while (cursor.moveToNext()) {
                        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("join-time query cancelled");
                        rowsRead++;
                        String content = cursor.getString(contentColumn);
                        int type = cursor.getInt(typeColumn);
                        long time = cursor.getLong(timeColumn);
                        long millis = time < 100000000000L ? time * 1000L : time;
                        if (millis <= 0L) { invalidTimeRows++; continue; }
                        boolean identityMatch = false, accepted = false;
                        for (String memberWxId : missingMembers) {
                            if (TextUtils.isEmpty(content) || !content.contains(memberWxId)) continue;
                            identityMatch = true;
                            if (!isMemberJoinSystemMessage(type, content, memberWxId)) continue;
                            accepted = true;
                            Long previous = result.get(memberWxId);
                            if (previous == null || millis < previous) result.put(memberWxId, millis);
                        }
                        if (identityMatch) identityRows++;
                        if (accepted) acceptedRows++;
                        if (!TextUtils.isEmpty(content)) {
                            String shape = "type=" + type + " identity=" + identityMatch + " accepted=" + accepted + " " + historyShape(content);
                            if (!structuralShapes.contains(shape)) {
                                if (structuralShapes.size() < 32) {
                                    structuralShapes.add(shape);
                                    log("JOIN_DIAG historyShape group=" + chatroomId + " table=" + table + " " + shape);
                                } else shapeOmittedRows++;
                            }
                        }
                    }
                } catch (Throwable error) {
                    result.complete = false;
                    log("JOIN_DIAG historyFailure group=" + chatroomId + " table=" + table
                            + " error=" + error.getClass().getName());
                    if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("join-time cancelled", error);
                } finally {
                    totalRows += rowsRead; totalIdentityRows += identityRows; totalAcceptedRows += acceptedRows;
                    log("JOIN_DIAG historyTable group=" + chatroomId + " table=" + table + " rows=" + rowsRead
                            + " shapeOmittedRows=" + shapeOmittedRows + " invalidTimeRows=" + invalidTimeRows + " identityRows=" + identityRows + " acceptedRows=" + acceptedRows);
                }
            }
            log("JOIN_DIAG historySummary group=" + chatroomId + " rows=" + totalRows + " identityRows=" + totalIdentityRows
                    + " acceptedRows=" + totalAcceptedRows + " missingBefore=" + missingMembers.size()
                    + " missingAfter=" + (members.size() - result.size())
                    + " complete=" + result.complete + " elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - queryStarted));
            log("群成员进群时间索引完成: group=" + chatroomId
                    + " tables=" + tables.size() + " members=" + members.size()
                    + " hits=" + result.size() + " roomdataHits=" + nativeHits
                    + " messageHits=" + (result.size() - nativeHits));
        } catch (Throwable e) {
            result.complete = false;
            log("建立群成员进群时间索引失败: group=" + chatroomId + " " + e.getMessage());
            // Do not discard verified native timestamps because a history fallback failed.
            if (result.isEmpty()) throw new IllegalStateException("群成员进群时间查询失败", e);
        }
        return result;
    }

    /** Bounded structural evidence: no message text, names, or raw member IDs. */
    private static final java.util.regex.Pattern HISTORY_TAG = java.util.regex.Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_:-]*)");
    private static final java.util.regex.Pattern HISTORY_ROLE = java.util.regex.Pattern.compile("<link\\b[^>]*\\bname=[\"']([^\"']+)[\"']");
    private static final java.util.regex.Pattern HISTORY_USER = java.util.regex.Pattern.compile("<username>(?:<!\\[CDATA\\[)?([^<\\]]+)");
    private static String historyShape(String content) {
        int originalLength = content.length();
        boolean truncated = originalLength > 16384;
        if (truncated) content = content.substring(0, 16384);
        java.util.regex.Matcher tags = HISTORY_TAG.matcher(content);
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        while (tags.find() && names.size() < 16) names.add(tags.group(2));
        java.util.Set<String> roles = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher links = HISTORY_ROLE.matcher(content);
        int roleCount = 0;
        while (links.find()) {
            roleCount++;
            if (roles.size() < 16) roles.add(Integer.toHexString(links.group(1).hashCode()));
        }
        java.util.List<String> users = new java.util.ArrayList<>();
        java.util.regex.Matcher ids = HISTORY_USER.matcher(content);
        int userCount = 0;
        while (ids.find()) {
            userCount++;
            if (users.size() < 16) users.add(Integer.toHexString(ids.group(1).hashCode()));
        }
        return "length=" + originalLength + " truncated=" + truncated + " tags=" + names + " roleCount=" + roleCount + " roleHashes=" + roles
                + " userCount=" + userCount + " userHashes=" + users
                + " joinWord=" + (content.contains("加入") || content.contains("进群"))
                + " inviteWord=" + (content.contains("邀请") || content.toLowerCase(java.util.Locale.ROOT).contains("invite"));
    }
    private void logHistoryTypeInventory(String groupId, String table, String quoted) {
        try (Cursor cursor = databaseApi.rawQuery("SELECT type, COUNT(*) AS n FROM " + quoted
                + " WHERE talker=? GROUP BY type", new String[]{groupId})) {
            if (cursor == null) throw new IllegalStateException("type inventory cursor unavailable");
            int types = 0;
            while (cursor.moveToNext()) {
                if (++types > 64) { log("JOIN_DIAG typeInventory group=" + groupId + " table=" + table + " state=truncated"); break; }
                log("JOIN_DIAG typeInventory group=" + groupId + " table=" + table + " type=" + cursor.getInt(0) + " rows=" + cursor.getLong(1));
            }
        } catch (Throwable error) {
            log("JOIN_DIAG typeInventory group=" + groupId + " table=" + table + " state=unavailable error=" + error.getClass().getName());
        }
    }
    /** Returns the persisted join-event time from the group's system messages, or zero when unavailable. */
    public long getMemberJoinTime(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(chatroomId) || TextUtils.isEmpty(memberWxId)) return 0L;
        Long time = getMemberJoinTimes(chatroomId).get(memberWxId);
        return time != null ? time : 0L;
    }
    private static long number(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private boolean isMemberJoinSystemMessage(int type, String content, String memberWxId) {
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(memberWxId)
                || !content.contains(memberWxId)) return false;
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        boolean systemType = WeChatMessageTypes.INSTANCE.isSystem(type)
                || type == 64 || type == 603979825 || type == 268445456
                || type == 268445458 || type == 285222674
                || type == 889192497 || type == 922746929;
        if (!systemType) return false;
        return lower.contains("加入") || lower.contains("邀请") || lower.contains("进群")
                || lower.contains("加入了群聊") || lower.contains("邀请加入")
                || lower.contains("addchatroommember") || lower.contains("invite")
                || lower.contains("sysmsgtemplate") || lower.contains("chatroommember")
                || lower.contains("linkname");
    }


    public List<String> getAdminMemberIds(String chatroomId) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, RoomDataMember> entry : getRoomDataMembers(chatroomId).entrySet()) {
            RoomDataMember member = entry.getValue();
            if (member != null && (member.roomFlag & ROOM_FLAG_ADMIN) != 0) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean addChatroomMember(String chatroomId, String addMember) {
        if (TextUtils.isEmpty(addMember)) return false;
        return addChatroomMember(chatroomId, Collections.singletonList(addMember));
    }

    public boolean addChatroomMember(String chatroomId, List<String> addMemberList) {
        if (TextUtils.isEmpty(chatroomId) || addMemberList == null || addMemberList.isEmpty()) {
            log("添加群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.addChatroomMemberCtor == null || networkDispatcher == null) {
            log("添加群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(addMemberList);
        if (members.isEmpty()) {
            log("添加群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.addChatroomMemberCtor, chatroomId, members, "", null);
            boolean result = networkDispatcher.send(scene);
            log("添加群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("添加群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean inviteChatroomMember(String chatroomId, String inviteMember) {
        if (TextUtils.isEmpty(inviteMember)) return false;
        return inviteChatroomMember(chatroomId, Collections.singletonList(inviteMember));
    }

    public boolean inviteChatroomMember(String chatroomId, List<String> inviteMemberList) {
        if (TextUtils.isEmpty(chatroomId) || inviteMemberList == null || inviteMemberList.isEmpty()) {
            log("邀请群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.inviteChatroomMemberCtor == null || networkDispatcher == null) {
            log("邀请群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(inviteMemberList);
        if (members.isEmpty()) {
            log("邀请群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.inviteChatroomMemberCtor, chatroomId, members, 0, null);
            boolean result = networkDispatcher.send(scene);
            log("邀请群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("邀请群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean delChatroomMember(String chatroomId, String delMember) {
        if (TextUtils.isEmpty(delMember)) return false;
        return delChatroomMember(chatroomId, Collections.singletonList(delMember));
    }

    public boolean delChatroomMember(String chatroomId, List<String> delMemberList) {
        if (TextUtils.isEmpty(chatroomId) || delMemberList == null || delMemberList.isEmpty()) {
            log("移除群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.delChatroomMemberCtor == null || networkDispatcher == null) {
            log("移除群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(delMemberList);
        if (members.isEmpty()) {
            log("移除群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.delChatroomMemberCtor, chatroomId, members, 0);
            boolean result = networkDispatcher.send(scene);
            log("移除群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("移除群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean isGroup(String wxId) {
        return contactApi != null && contactApi.isGroup(wxId);
    }

    private void installNetworkHook() {
        if (networkHookInstalled || networkDispatcher == null || dexFinder == null) return;
        if (dexFinder.netQueueClass == null && dexFinder.netQueueCandidateClasses.isEmpty()) return;
        networkDispatcher.hookNetworkQueue(dexFinder.netQueueClass, dexFinder.netQueueCandidateClasses);
        networkHookInstalled = true;
    }

    private List<String> cleanMemberList(List<String> members) {
        List<String> result = new ArrayList<>();
        if (members == null) return result;
        for (String member : members) {
            if (TextUtils.isEmpty(member) || result.contains(member)) continue;
            result.add(member);
        }
        return result;
    }

    private List<String> splitMembers(String memberList) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(memberList)) return result;
        for (String part : memberList.split(";")) {
            if (!TextUtils.isEmpty(part)) result.add(part);
        }
        return result;
    }

    /** Read-only probe for the verified 8.0.78 gate; never overrides configuration. */
    public void logNativeJoinGate(ClassLoader loader) {
        if (loader == null) { log("JOIN_DIAG gate state=loader-unavailable"); return; }
        try {
            Class<?> config = Class.forName("com.tencent.mm.repairer.config.profile.RepairerConfigChatroomJoinInfo", false, loader);
            Class<?> service = Class.forName("lb2.e", false, loader);
            Class<?> key = Class.forName("od5.e", false, loader);
            Object instance = service.getDeclaredMethod("g").invoke(null);
            Object value = service.getDeclaredMethod("c", key).invoke(instance, config.getDeclaredConstructor().newInstance());
            log("JOIN_DIAG gate state=read value=" + value);
        } catch (Throwable error) {
            log("JOIN_DIAG gate state=unavailable error=" + error.getClass().getSimpleName());
        }
    }

    private static final class NativeJoinDiagnostics {
        String blobHash = "unavailable";
        String state = "not-started";
        int bytes, entries, named, duplicates, parseErrors, absent, zero, early, overflow, future, valid;
        @Override public String toString() {
            return "state=" + state + " blobHash=" + blobHash + " bytes=" + bytes + " entries=" + entries + " named=" + named
                    + " duplicates=" + duplicates + " parseErrors=" + parseErrors + " absent=" + absent
                    + " zero=" + zero + " early=" + early + " overflow=" + overflow
                    + " future=" + future + " valid=" + valid;
        }
    }

    private Map<String, RoomDataMember> getRoomDataMembers(String chatroomId) {
        return getRoomDataMembers(chatroomId, null);
    }

    private Map<String, RoomDataMember> getRoomDataMembers(String chatroomId, NativeJoinDiagnostics d) {
        Map<String, RoomDataMember> result = new LinkedHashMap<>();
        if (TextUtils.isEmpty(chatroomId) || databaseApi == null) {
            if (d != null) d.state = "input-unavailable";
            return result;
        }
        if (!hasColumn("chatroom", "roomdata")) {
            if (d != null) d.state = "column-unavailable";
            return result;
        }
        try (Cursor cursor = databaseApi.rawQuery(
                "SELECT roomdata FROM chatroom WHERE chatroomname=? LIMIT 1", new String[]{chatroomId})) {
            if (cursor == null) { if (d != null) d.state = "cursor-unavailable"; return result; }
            if (!cursor.moveToFirst()) { if (d != null) d.state = "row-absent"; return result; }
            int column = cursor.getColumnIndexOrThrow("roomdata");
            byte[] roomData;
            if (cursor.getType(column) == Cursor.FIELD_TYPE_STRING) roomData = hexStringToBytes(cursor.getString(column));
            else roomData = cursor.getBlob(column);
            if (roomData == null || roomData.length == 0) {
                if (d != null) d.state = "blob-empty";
                return result;
            }
            if (d != null) {
                d.bytes = roomData.length;
                d.state = "parsing";
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(roomData);
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                d.blobHash = hex.toString();
            }
            ProtoReader reader = new ProtoReader(roomData);
            while (!reader.isAtEnd()) {
                int tag = reader.readVarint32();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (fieldNumber == 1 && wireType == 2) {
                    RoomDataMember member = parseRoomDataMember(reader.readBytes());
                    if (d != null) {
                        d.entries++;
                        if (member.parseFailed) d.parseErrors++;
                        else if (!member.joinTimePresent) d.absent++;
                        else if (member.rawJoinTime == 0L) d.zero++;
                        else if (member.rawJoinTime < 946684800L) d.early++;
                        else if (member.rawJoinTime > Integer.MAX_VALUE) d.overflow++;
                        else if (member.joinTimeMillis == 0L) d.future++;
                        else d.valid++;
                    }
                    if (!TextUtils.isEmpty(member.username)) {
                        if (d != null) { d.named++; if (result.containsKey(member.username)) d.duplicates++; }
                        result.put(member.username, member);
                    }
                } else reader.skip(wireType);
            }
            if (d != null) d.state = "complete";
        } catch (Throwable e) {
            if (d != null) d.state = "read-or-envelope-error:" + e.getClass().getSimpleName();
            log("解析群成员身份失败: " + e.getMessage());
        }
        return result;
    }

    private RoomDataMember parseRoomDataMember(byte[] memberData) {
        RoomDataMember member = new RoomDataMember();
        try {
            ProtoReader reader = new ProtoReader(memberData);
            while (!reader.isAtEnd()) {
                int tag = reader.readVarint32();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (wireType == 2 && fieldNumber == 1) {
                    member.username = reader.readString().trim();
                } else if (wireType == 2 && fieldNumber == 2) {
                    member.displayName = reader.readString().trim();
                } else if (wireType == 0 && fieldNumber == 3) {
                    member.roomFlag = reader.readVarint32();
                } else if (wireType == 2 && fieldNumber == 4) {
                    member.inviterWxId = reader.readString().trim();
                } else if (wireType == 0 && fieldNumber == 6) {
                    member.joinTimePresent = true;
                    member.rawJoinTime = reader.readVarint64();
                    member.joinTimeMillis = normalizeNativeJoinTime(member.rawJoinTime);
                } else {
                    reader.skip(wireType);
                }
            }
        } catch (Throwable ignored) {
            RoomDataMember failed = new RoomDataMember();
            failed.parseFailed = true;
            return failed;
        }
        return member;
    }

    private static long normalizeNativeJoinTime(long seconds) {
        // Native protocol is int32 seconds. Reject missing, corrupt and future timestamps.
        if (seconds < 946684800L || seconds > Integer.MAX_VALUE
                || seconds > System.currentTimeMillis() / 1000L + 86400L) return 0L;
        return seconds * 1000L;
    }

    private boolean hasColumn(String table, String column) {
        if (TextUtils.isEmpty(table) || TextUtils.isEmpty(column) || databaseApi == null) return false;
        String key = table + ":" + column;
        Boolean cached = columnExistsCache.get(key);
        if (cached != null) return cached;
        boolean exists = false;
        try {
            List<Map<String, Object>> rows = databaseApi.query("PRAGMA table_info(" + table + ")", null);
            for (Map<String, Object> row : rows) {
                if (column.equalsIgnoreCase(str(row, "name"))) {
                    exists = true;
                    break;
                }
            }
        } catch (Throwable e) {
            log("检测字段失败: " + table + "." + column + " " + e.getMessage());
        }
        if (exists) {
            columnExistsCache.put(key, true);
        } else {
            columnExistsCache.remove(key);
        }
        return exists;
    }

    private static byte[] blob(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof byte[]) return (byte[]) value;
        if (value instanceof String) return hexStringToBytes((String) value);
        if (value == null) return null;
        try {
            Method method = value.getClass().getMethod("toByteArray");
            Object result = method.invoke(value);
            if (result instanceof byte[]) return (byte[]) result;
        } catch (Throwable ignored) {}
        try {
            Method method = value.getClass().getMethod("getBytes");
            Object result = method.invoke(value);
            if (result instanceof byte[]) return (byte[]) result;
        } catch (Throwable ignored) {}
        return null;
    }

    private static byte[] hexStringToBytes(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String s = value.trim();
        if (s.startsWith("hex->")) s = s.substring(5);
        s = s.replace(" ", "").replace("\n", "").replace("\r", "");
        if (s.length() < 2 || (s.length() & 1) != 0) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return null;
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatChatroomApi] " + message);
    }

    private static final class RoomDataMember {
        String username = "";
        String displayName = "";
        String inviterWxId = "";
        long joinTimeMillis;
        long rawJoinTime;
        boolean joinTimePresent, parseFailed;
        int roomFlag;
    }

    private static final class ProtoReader {
        private final byte[] data;
        private int position;

        ProtoReader(byte[] data) {
            this.data = data != null ? data : new byte[0];
        }

        boolean isAtEnd() {
            return position >= data.length;
        }

        int readVarint32() {
            long value = readVarint64();
            if (value > Integer.MAX_VALUE) {
                throw new IllegalStateException("varint过大");
            }
            return (int) value;
        }

        long readFixed64() {
            require(8);
            long result = 0L;
            for (int i = 0; i < 8; i++) {
                result |= (long) (data[position++] & 0xFF) << (i * 8);
            }
            return result;
        }
        long readVarint64() {
            long result = 0L;
            for (int shift = 0; shift < 64; shift += 7) {
                require(1);
                int b = data[position++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
            }
            throw new IllegalStateException("varint异常");
        }

        String readString() {
            byte[] bytes = readBytes();
            try {
                return new String(bytes, "UTF-8");
            } catch (Throwable ignored) {
                return new String(bytes);
            }
        }

        byte[] readBytes() {
            int length = readVarint32();
            if (length < 0 || length > data.length - position) {
                throw new IllegalStateException("bytes长度异常");
            }
            byte[] bytes = new byte[length];
            System.arraycopy(data, position, bytes, 0, length);
            position += length;
            return bytes;
        }

        void skip(int wireType) {
            switch (wireType) {
                case 0:
                    readVarint64();
                    return;
                case 1:
                    skipBytes(8);
                    return;
                case 2:
                    skipBytes(readVarint32());
                    return;
                case 5:
                    skipBytes(4);
                    return;
                default:
                    throw new IllegalStateException("不支持的wireType: " + wireType);
            }
        }

        private void skipBytes(int count) {
            if (count < 0) throw new IllegalStateException("跳过长度异常");
            require(count);
            position += count;
        }

        private void require(int count) {
            if (position + count > data.length) {
                throw new IllegalStateException("protobuf长度不足");
            }
        }
    }
}
