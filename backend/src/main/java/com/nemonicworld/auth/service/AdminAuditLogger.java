package com.nemonicworld.auth.service;

import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditLogger {

    private static final String TARGET_TYPE_ADMIN_ACCOUNT = "admin_account";
    private static final String TARGET_TYPE_MEMO = "memo";
    private static final String HIDDEN_REASON_ADMIN_HIDDEN = "admin_hidden";
    private static final String UNKNOWN = "unknown";

    public void logLoginSuccess(AdminUser adminUser, AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminUser.getId().toString(), adminUser.getRole().getValue(),
            clientInfo, adminUser.getId().toString(), "login", "success");
        emit("INFO", "admin_login_success", "admin login succeeded", clientInfo, metadata);
    }

    public void logLoginFailure(String attemptedLoginId, AdminUser adminUser, AdminClientInfo clientInfo) {
        String actorId = adminUser == null ? UNKNOWN : adminUser.getId().toString();
        String actorRole = adminUser == null ? UNKNOWN : adminUser.getRole().getValue();
        Map<String, Object> metadata = baseMetadata(actorId, actorRole, clientInfo, attemptedLoginId, "login",
            "failure");
        emit("WARN", "admin_login_failed", "admin login failed", clientInfo, metadata);
    }

    public void logLogout(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, adminPrincipal.id().toString(), "logout", "success");
        emit("INFO", "admin_logout", "admin logout succeeded", clientInfo, metadata);
    }

    public void logAdminAccountCreate(AdminPrincipal adminPrincipal, AdminUser createdAdmin,
        AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, createdAdmin.getId().toString(), "create", "success");
        metadata.put("after", adminAccountSnapshot(createdAdmin));
        emit("INFO", "admin_account_create", "admin account created", clientInfo, metadata);
    }

    public void logAdminAccountDelete(AdminPrincipal adminPrincipal, AdminUser deletedAdmin,
        AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, deletedAdmin.getId().toString(), "delete", "success");
        metadata.put("before", adminAccountSnapshot(deletedAdmin));
        emit("INFO", "admin_account_delete", "admin account deleted", clientInfo, metadata);
    }

    public void logCommunityMemoHideRequested(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "hide", "requested");
        metadata.put("input_reason", reason);
        emit("INFO", "admin_community_memo_hide_requested", "community memo hide requested by admin", clientInfo,
            metadata);
    }

    public void logCommunityMemoHidden(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged) {
        logCommunityMemoHidden(adminPrincipal, memoId, reason, clientInfo, stateChanged, Map.of());
    }

    public void logCommunityMemoHidden(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged, Map<String, Object> stateMetadata) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "hide", "success");
        metadata.put("input_reason", reason);
        metadata.put("hidden_reason", HIDDEN_REASON_ADMIN_HIDDEN);
        metadata.put("hidden_reason_detail", reason);
        metadata.put("state_changed", stateChanged);
        metadata.putAll(stateMetadata == null ? Map.of() : stateMetadata);
        emit("INFO", stateChanged ? "admin_community_memo_hidden" : "admin_community_memo_hide_noop",
            "community memo hidden by admin", clientInfo, metadata);
    }

    public void logCommunityMemoRestoreRequested(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "restore", "requested");
        metadata.put("input_reason", reason);
        emit("INFO", "admin_community_memo_restore_requested", "community memo restore requested by admin", clientInfo,
            metadata);
    }

    public void logCommunityMemoRestored(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged) {
        logCommunityMemoRestored(adminPrincipal, memoId, reason, clientInfo, stateChanged, Map.of());
    }

    public void logCommunityMemoRestored(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged, Map<String, Object> stateMetadata) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "restore", "success");
        metadata.put("restore_reason_detail", reason);
        metadata.put("state_changed", stateChanged);
        metadata.putAll(stateMetadata == null ? Map.of() : stateMetadata);
        emit("INFO", stateChanged ? "admin_community_memo_restored" : "admin_community_memo_restore_noop",
            "community memo restored by admin", clientInfo, metadata);
    }

    public void logMemoSoftDelete(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "delete", "success");
        metadata.put("reason", reason);
        metadata.put("state_changed", stateChanged);
        if (stateChanged) {
            metadata.put("before", memoHiddenState(false, null));
            metadata.put("after", memoHiddenState(true, HIDDEN_REASON_ADMIN_HIDDEN));
        }
        emit("INFO", "memo_soft_delete", "community memo soft deleted by admin", clientInfo, metadata);
    }

    public void logMemoRestore(AdminPrincipal adminPrincipal, String memoId, String reason, AdminClientInfo clientInfo,
        boolean stateChanged, String previousHiddenReason) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "restore", "success");
        metadata.put("reason", reason);
        metadata.put("state_changed", stateChanged);
        if (stateChanged) {
            metadata.put("before", memoHiddenState(true, previousHiddenReason));
            metadata.put("after", memoHiddenState(false, null));
        }
        emit("INFO", "memo_restore", "community memo restored by admin", clientInfo, metadata);
    }

    public void logCommunityMemoListViewed(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, int page, int size,
        long totalElements) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, "community_memo_list", "view", "success");
        metadata.put("hidden", hidden);
        metadata.put("moderation_status", moderationStatus);
        metadata.put("source_type", sourceType);
        metadata.put("reported", reported);
        metadata.put("keyword_present", keyword != null);
        metadata.put("page", page);
        metadata.put("size", size);
        metadata.put("total_elements", totalElements);
        emit("INFO", "admin_community_memo_list_viewed", "community memo list viewed by admin", clientInfo, metadata);
    }

    public void logCommunityMemoDetailViewed(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo, String memoId,
        boolean hidden, int reportCount) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "view_detail", "success");
        metadata.put("hidden", hidden);
        metadata.put("report_count", reportCount);
        emit("INFO", "admin_community_memo_detail_viewed", "community memo detail viewed by admin", clientInfo,
            metadata);
    }

    public void logCommunityMemoReportsViewed(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo, String memoId,
        String reason, int page, int size, long totalElements) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "view_reports", "success");
        metadata.put("reason", reason);
        metadata.put("page", page);
        metadata.put("size", size);
        metadata.put("total_elements", totalElements);
        emit("INFO", "admin_community_memo_reports_viewed", "community memo reports viewed by admin", clientInfo,
            metadata);
    }

    public void logCommunityMemoSearchFailed(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo,
        String operation, String targetId, String reasonCode) {
        String actorId = adminPrincipal == null ? UNKNOWN : adminPrincipal.id().toString();
        String actorRole = adminPrincipal == null ? UNKNOWN : adminPrincipal.role().getValue();
        Map<String, Object> metadata = baseMetadata(actorId, actorRole, clientInfo, TARGET_TYPE_MEMO, targetId,
            operation, "failure");
        metadata.put("reason_code", reasonCode);
        emit("WARN", "admin_community_memo_search_failed", "admin community memo search failed", clientInfo, metadata);
    }

    public void logInquiryStatusChange(AdminPrincipal adminPrincipal, String inquiryId, String beforeStatus,
        String afterStatus, AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, "inquiry", inquiryId, "update", "success");
        metadata.put("before", inquiryStatusSnapshot(beforeStatus));
        metadata.put("after", inquiryStatusSnapshot(afterStatus));
        emit("INFO", "inquiry_status_change", "inquiry status changed by admin", clientInfo, metadata);
    }

    public void logInquiryReplySend(AdminPrincipal adminPrincipal, String inquiryId, String beforeStatus,
        String afterStatus, AdminClientInfo clientInfo) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, "inquiry", inquiryId, "send", "success");
        metadata.put("before", inquiryStatusSnapshot(beforeStatus));
        metadata.put("after", inquiryReplySnapshot(afterStatus, adminPrincipal.id().toString()));
        emit("INFO", "inquiry_reply_send", "inquiry reply sent by admin", clientInfo, metadata);
    }

    public void logPromptUpdate(AdminPrincipal adminPrincipal, String promptId, String action,
        AdminClientInfo clientInfo, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, "prompt", promptId, action, "success");
        if (before != null && !before.isEmpty()) {
            metadata.put("before", before);
        }
        if (after != null && !after.isEmpty()) {
            metadata.put("after", after);
        }
        emit("INFO", "prompt_update", "GMS prompt changed by admin", clientInfo, metadata);
    }

    public void logParamChange(AdminPrincipal adminPrincipal, String targetId, AdminClientInfo clientInfo,
        Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, "param", targetId, "update", "success");
        if (before != null && !before.isEmpty()) {
            metadata.put("before", before);
        }
        if (after != null && !after.isEmpty()) {
            metadata.put("after", after);
        }
        emit("INFO", "param_change", "system parameter changed by admin", clientInfo, metadata);
    }

    public void logRelayRoomForceClose(AdminPrincipal adminPrincipal, String roomCode, String beforeStatus,
        AdminClientInfo clientInfo) {
        logRoomForceClose(adminPrincipal, roomCode, beforeStatus, clientInfo, "relay_room_force_close",
            "relay room force closed by admin");
    }

    public void logFlipbookRoomForceClose(AdminPrincipal adminPrincipal, String roomCode, String beforeStatus,
        AdminClientInfo clientInfo) {
        logRoomForceClose(adminPrincipal, roomCode, beforeStatus, clientInfo, "flipbook_room_force_close",
            "flipbook room force closed by admin");
    }

    public void logInfiniteCanvasForceClose(AdminPrincipal adminPrincipal, String canvasId, String beforeStatus,
        AdminClientInfo clientInfo) {
        logRoomForceClose(adminPrincipal, canvasId, beforeStatus, clientInfo, "infinite_canvas_force_close",
            "infinite canvas force closed by admin");
    }

    private Map<String, Object> baseMetadata(String actorId, String actorRole, AdminClientInfo clientInfo,
        String targetId, String action, String result) {
        return baseMetadata(actorId, actorRole, clientInfo, TARGET_TYPE_ADMIN_ACCOUNT, targetId, action, result);
    }

    private Map<String, Object> baseMetadata(String actorId, String actorRole, AdminClientInfo clientInfo,
        String targetType, String targetId, String action, String result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_type", "admin");
        metadata.put("admin_id", actorId);
        metadata.put("admin_role", actorRole);
        metadata.put("actor_id", actorId);
        metadata.put("actor_role", actorRole);
        metadata.put("actor_ip", clientInfo.ipAddress());
        metadata.put("trace_id", clientInfo.traceId());
        metadata.put("target_type", targetType);
        metadata.put("target_id", targetId);
        metadata.put("action", action);
        metadata.put("result", result);

        return metadata;
    }

    private Map<String, Object> adminAccountSnapshot(AdminUser adminUser) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", adminUser.getId().toString());
        snapshot.put("login_id", adminUser.getLoginId());
        snapshot.put("nickname", adminUser.getNickname());
        snapshot.put("role", adminUser.getRole().getValue());

        return snapshot;
    }

    private Map<String, Object> memoHiddenState(boolean hidden, String hiddenReason) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("is_hidden", hidden);
        state.put("hidden_reason", hiddenReason);

        return state;
    }

    private Map<String, Object> inquiryStatusSnapshot(String status) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", status);

        return snapshot;
    }

    private Map<String, Object> inquiryReplySnapshot(String status, String assignedTo) {
        Map<String, Object> snapshot = inquiryStatusSnapshot(status);
        snapshot.put("assigned_to", assignedTo);

        return snapshot;
    }

    private void logRoomForceClose(AdminPrincipal adminPrincipal, String roomCode, String beforeStatus,
        AdminClientInfo clientInfo, String eventName, String message) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, "room", roomCode, "force_close", "success");
        metadata.put("before", roomStatusSnapshot(beforeStatus));
        metadata.put("after", roomStatusSnapshot("CLOSED"));
        emit("INFO", eventName, message, clientInfo, metadata);
    }

    private Map<String, Object> roomStatusSnapshot(String status) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", status);

        return snapshot;
    }

    private void emit(String level, String eventName, String message, AdminClientInfo clientInfo,
        Map<String, Object> metadata) {
        if ("WARN".equals(level)) {
            StructuredEventLogger.auditWarn(eventName, message, clientInfo.traceId(), metadata);
            return;
        }

        StructuredEventLogger.audit(eventName, message, clientInfo.traceId(), metadata);
    }
}
