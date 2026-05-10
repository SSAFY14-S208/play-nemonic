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
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "hide", "success");
        metadata.put("input_reason", reason);
        metadata.put("hidden_reason", "admin_hidden");
        metadata.put("hidden_reason_detail", reason);
        metadata.put("state_changed", stateChanged);
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
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "restore", "success");
        metadata.put("restore_reason_detail", reason);
        metadata.put("state_changed", stateChanged);
        emit("INFO", stateChanged ? "admin_community_memo_restored" : "admin_community_memo_restore_noop",
            "community memo restored by admin", clientInfo, metadata);
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

    private Map<String, Object> baseMetadata(String actorId, String actorRole, AdminClientInfo clientInfo,
        String targetId, String action, String result) {
        return baseMetadata(actorId, actorRole, clientInfo, TARGET_TYPE_ADMIN_ACCOUNT, targetId, action, result);
    }

    private Map<String, Object> baseMetadata(String actorId, String actorRole, AdminClientInfo clientInfo,
        String targetType, String targetId, String action, String result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_id", actorId);
        metadata.put("actor_role", actorRole);
        metadata.put("actor_ip", clientInfo.ipAddress());
        metadata.put("target_type", targetType);
        metadata.put("target_id", targetId);
        metadata.put("action", action);
        metadata.put("result", result);

        return metadata;
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
