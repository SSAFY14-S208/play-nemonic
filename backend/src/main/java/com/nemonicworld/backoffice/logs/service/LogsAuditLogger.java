package com.nemonicworld.backoffice.logs.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.logs.dto.request.LogsTimeRange;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LogsAuditLogger {

    private static final int QUERY_MAX_LENGTH = 1024;

    public void logQuery(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo, String endpoint, String index,
        String query, LogsTimeRange timeRange, long resultTotal, long tookMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_type", "admin");
        metadata.put("admin_id", adminPrincipal.id().toString());
        metadata.put("admin_role", adminPrincipal.role().getValue());
        metadata.put("actor_id", adminPrincipal.id().toString());
        metadata.put("actor_role", adminPrincipal.role().getValue());
        metadata.put("actor_ip", clientInfo.ipAddress());
        metadata.put("trace_id", clientInfo.traceId());
        metadata.put("endpoint", endpoint);
        metadata.put("index", index);
        metadata.put("query", truncate(query == null ? "" : query));
        metadata.put("time_from", timeRange.from());
        metadata.put("time_to", timeRange.to());
        metadata.put("result_total", resultTotal);
        metadata.put("took_ms", tookMs);

        StructuredEventLogger.audit("admin_logs_query", "admin logs query", clientInfo.traceId(), metadata);
    }

    private String truncate(String value) {
        if (value.length() <= QUERY_MAX_LENGTH) {
            return value;
        }

        return value.substring(0, QUERY_MAX_LENGTH);
    }
}
