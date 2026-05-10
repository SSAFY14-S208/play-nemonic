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
        emit("INFO", "admin_login", "admin login succeeded", clientInfo, metadata);
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

    public void logCommunityMemoHide(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "hide", "success");
        metadata.put("reason", reason);
        metadata.put("hidden_reason", "admin_hidden");
        metadata.put("state_changed", stateChanged);
        emit("INFO", "community_memo_hide", "community memo hidden by admin", clientInfo, metadata);
    }

    public void logCommunityMemoRestore(AdminPrincipal adminPrincipal, String memoId, String reason,
        AdminClientInfo clientInfo, boolean stateChanged) {
        Map<String, Object> metadata = baseMetadata(adminPrincipal.id().toString(), adminPrincipal.role().getValue(),
            clientInfo, TARGET_TYPE_MEMO, memoId, "restore", "success");
        metadata.put("reason", reason);
        metadata.put("state_changed", stateChanged);
        emit("INFO", "community_memo_restore", "community memo restored by admin", clientInfo, metadata);
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
