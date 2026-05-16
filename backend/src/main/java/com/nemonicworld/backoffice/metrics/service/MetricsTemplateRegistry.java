package com.nemonicworld.backoffice.metrics.service;

import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 백오피스 통계 페이지가 호출할 수 있는 PromQL 템플릿 화이트리스트. 프론트엔드는 raw PromQL 대신 템플릿 id와 파라미터 맵을
 * 전달하고, 본 클래스가 PromQL을 빌드한다.
 */
@Component
public class MetricsTemplateRegistry {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d+(s|m|h|d)$");
    private static final Pattern STEP_PATTERN = Pattern.compile("^\\d+(s|m|h)$");
    private static final Set<String> WINDOW_DURATIONS = Set.of("1m", "5m", "15m");
    private static final Set<String> QUANTILES = Set.of("0.5", "0.95", "0.99");
    private static final Set<String> JOB_NAMES = Set.of("prometheus", "node", "cadvisor", "spring-app",
        "moderation-server", "nginx");

    private final Map<String, Template> templates;

    public MetricsTemplateRegistry() {
        this.templates = Collections.unmodifiableMap(buildTemplates());
    }

    public String buildPromql(String templateId, Map<String, String> params) {
        Template template = templates.get(templateId);
        if (template == null) {
            throw AdminMetricsException.unknownTemplate();
        }
        Map<String, String> safeParams = params == null ? Map.of() : params;

        return template.build(safeParams);
    }

    public boolean hasTemplate(String templateId) {
        return templates.containsKey(templateId);
    }

    public Set<String> templateIds() {
        return templates.keySet();
    }

    private Map<String, Template> buildTemplates() {
        Map<String, Template> result = new LinkedHashMap<>();

        // Row 1 — 서비스 상태
        // service_up_status: up{job=~"#{job}"} — Prometheus scrape job 이름은
        // prometheus.yml 기준
        result.put("service_up_status",
            new Template(Set.of("job"), params -> "up{job=\"" + requireEnum(params, "job", JOB_NAMES) + "\"}"));

        // Row 2 — 요청·응답 (Grafana 원본은 1m window이지만 사용자가 윈도우를 조정할 수 있도록 DURATION 파라미터 노출)
        result.put("nginx_request_rate", new Template(Set.of("window"),
            params -> "sum(rate(nginx_http_requests_total[" + requireEnum(params, "window", WINDOW_DURATIONS) + "]))"));

        result.put("nginx_active_connections", new Template(Set.of(), params -> "nginx_connections_active"));

        // spring-app job 필터 추가 (Grafana 원본과 일치)
        result.put("spring_http_rps_by_service",
            new Template(Set.of("window"),
                params -> "sum by (service) (rate(http_server_requests_seconds_count{job=\"spring-app\"}["
                    + requireDuration(params, "window") + "]))"));

        result.put("spring_http_latency_quantile",
            new Template(Set.of("quantile", "window"),
                params -> "histogram_quantile(" + requireEnum(params, "quantile", QUANTILES)
                    + ", sum by (le) (rate(http_server_requests_seconds_bucket{job=\"spring-app\"}["
                    + requireDuration(params, "window") + "])))"));

        // Grafana 는 clamp_min 분모를 사용; FE 는 NaN 가능성 회피용. 여기서도 동일하게 적용.
        result.put("spring_http_error_ratio", new Template(Set.of("window"), params -> {
            String window = requireDuration(params, "window");

            return "sum(rate(http_server_requests_seconds_count{job=\"spring-app\",status=~\"5..\"}[" + window
                + "])) / clamp_min(sum(rate(http_server_requests_seconds_count{job=\"spring-app\"}[" + window
                + "])), 1e-9)";
        }));

        // Row 3 — WebSocket
        result.put("ws_active_sessions", new Template(Set.of(), params -> "nemonic_ws_active_sessions"));

        result.put("ws_connect_rate", new Template(Set.of("window"),
            params -> "sum(rate(nemonic_ws_connect_total[" + requireDuration(params, "window") + "]))"));

        result.put("ws_disconnect_rate", new Template(Set.of("window"),
            params -> "sum(rate(nemonic_ws_disconnect_total[" + requireDuration(params, "window") + "]))"));

        // Row 4 — content
        result.put("content_active_rooms_by_type",
            new Template(Set.of(), params -> "sum by (content_type) (nemonic_content_active_rooms)"));

        // Row 5 — host resources
        // Grafana 원본: 1 - avg by (instance) (rate(... idle ...)) → percentunit 0~1
        // 명세서는 백분율 형태(0~100)를 요청하므로 100 - (... * 100) 으로 노출
        result.put("host_cpu_usage_percent", new Template(Set.of("window"), params -> {
            String window = requireDuration(params, "window");

            return "100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[" + window + "])) * 100)";
        }));

        result.put("host_memory_usage_percent", new Template(Set.of(),
            params -> "(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100"));

        result.put("host_disk_usage_percent", new Template(Set.of(),
            params -> "(node_filesystem_size_bytes{mountpoint=\"/\"} - node_filesystem_avail_bytes{mountpoint=\"/\"})"
                + " / node_filesystem_size_bytes{mountpoint=\"/\"} * 100"));

        result.put("host_network_receive_bytes",
            new Template(Set.of("window"), params -> "sum by (device) (rate(node_network_receive_bytes_total["
                + requireDuration(params, "window") + "]))"));

        result.put("host_network_transmit_bytes",
            new Template(Set.of("window"), params -> "sum by (device) (rate(node_network_transmit_bytes_total["
                + requireDuration(params, "window") + "]))"));

        return result;
    }

    private static String requireDuration(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || !DURATION_PATTERN.matcher(value).matches()) {
            throw AdminMetricsException.invalidParam();
        }

        return value;
    }

    private static String requireEnum(Map<String, String> params, String key, Set<String> allowedValues) {
        String value = params.get(key);
        if (value == null || !allowedValues.contains(value)) {
            throw AdminMetricsException.invalidParam();
        }

        return value;
    }

    public static boolean isValidStep(String step) {
        if (step == null || !STEP_PATTERN.matcher(step).matches()) {
            return false;
        }
        long seconds = parseStepSeconds(step);

        return seconds >= 5L && seconds <= 3_600L;
    }

    public static long parseStepSeconds(String step) {
        int length = step.length();
        long magnitude = Long.parseLong(step.substring(0, length - 1));
        char unit = step.charAt(length - 1);

        return switch (unit) {
            case 's' -> magnitude;
            case 'm' -> magnitude * 60L;
            case 'h' -> magnitude * 3_600L;
            default -> throw AdminMetricsException.invalidParam();
        };
    }

    /**
     * 템플릿 1건의 정의.
     */
    private record Template(Set<String> requiredParams,
        java.util.function.Function<Map<String, String>, String> builder) {

        String build(Map<String, String> params) {
            // 알 수 없는 키가 섞여 들어와도 정보 노출 없이 거부
            Set<String> incoming = new LinkedHashSet<>(params.keySet());
            for (String key : incoming) {
                if (!requiredParams.contains(key)) {
                    throw AdminMetricsException.invalidParam();
                }
            }
            for (String required : requiredParams) {
                if (!params.containsKey(required)) {
                    throw AdminMetricsException.invalidParam();
                }
            }

            return builder.apply(params);
        }

        @SuppressWarnings("unused")
        List<String> paramKeys() {
            return List.copyOf(requiredParams);
        }
    }
}
