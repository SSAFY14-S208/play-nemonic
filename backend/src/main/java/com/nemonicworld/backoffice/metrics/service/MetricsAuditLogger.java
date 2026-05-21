package com.nemonicworld.backoffice.metrics.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsTimeRange;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Admin 메트릭 API 호출에 대한 감사 로그. 파라미터 값은 잠재적 PII 회피 차원에서 키 이름만 기록한다.
 */
@Component
public class MetricsAuditLogger {

    public void logQuery(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo, String endpoint, String template,
        Map<String, String> params, MetricsTimeRange timeRange, String step) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actor_type", "admin");
        metadata.put("admin_id", adminPrincipal.id().toString());
        metadata.put("admin_role", adminPrincipal.role().getValue());
        metadata.put("actor_id", adminPrincipal.id().toString());
        metadata.put("actor_role", adminPrincipal.role().getValue());
        metadata.put("actor_ip", clientInfo.ipAddress());
        metadata.put("trace_id", clientInfo.traceId());
        metadata.put("endpoint", endpoint);
        metadata.put("template", template);
        metadata.put("param_keys", sortedKeys(params));
        if (timeRange != null) {
            metadata.put("time_from", timeRange.from());
            metadata.put("time_to", timeRange.to());
        }
        if (step != null) {
            metadata.put("step", step);
        }

        StructuredEventLogger.audit("admin_metrics_query", "admin metrics query", clientInfo.traceId(), metadata);
    }

    private Set<String> sortedKeys(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Set.of();
        }

        return new TreeSet<>(params.keySet());
    }
}
