package com.nemonicworld.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogger.class);
    private static final String SERVICE_NAME = "backoffice-api";
    private static final String TARGET_TYPE_ADMIN_ACCOUNT = "admin_account";
    private static final String TARGET_TYPE_MEMO = "memo";
    private static final String UNKNOWN = "unknown";

    private final ObjectMapper objectMapper;

    public AdminAuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

    private Map<String, Object> adminAccountSnapshot(AdminUser adminUser) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", adminUser.getId().toString());
        snapshot.put("login_id", adminUser.getLoginId());
        snapshot.put("nickname", adminUser.getNickname());
        snapshot.put("role", adminUser.getRole().getValue());

        return snapshot;
    }

    private void emit(String level, String eventName, String message, AdminClientInfo clientInfo,
        Map<String, Object> metadata) {
        Map<String, Object> auditLog = new LinkedHashMap<>();
        auditLog.put("@timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        auditLog.put("level", level);
        auditLog.put("service", SERVICE_NAME);
        auditLog.put("trace_id", clientInfo.traceId());
        auditLog.put("event_name", eventName);
        auditLog.put("message", message);
        auditLog.put("metadata", metadata);

        try {
            System.out.println(objectMapper.writeValueAsString(auditLog));
        } catch (JsonProcessingException e) {
            log.error("audit_log_emit_failure event_name={}", eventName, e);
        }
    }
}
