package com.nemonicworld.backoffice.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsQueryRangeRequest;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsQueryRequest;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsTimeRange;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsSampleResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsSeriesResponse;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsServiceImpl implements AdminMetricsService {

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(30);
    private static final String RESULT_TYPE_VECTOR = "vector";
    private static final String RESULT_TYPE_MATRIX = "matrix";

    private final ObjectMapper objectMapper;
    private final MetricsTemplateRegistry templateRegistry;
    private final PrometheusClient prometheusClient;
    private final MetricsAuditLogger auditLogger;
    private final Cache<String, Object> cache;

    public AdminMetricsServiceImpl(ObjectMapper objectMapper, MetricsTemplateRegistry templateRegistry,
        PrometheusClient prometheusClient, MetricsAuditLogger auditLogger, Cache<String, Object> adminMetricsCache) {
        this.objectMapper = objectMapper;
        this.templateRegistry = templateRegistry;
        this.prometheusClient = prometheusClient;
        this.auditLogger = auditLogger;
        this.cache = adminMetricsCache;
    }

    @Override
    public MetricsQueryResponse query(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        MetricsQueryRequest request = readRequest(requestNode, MetricsQueryRequest.class);
        requireTemplate(request.template());
        String promql = templateRegistry.buildPromql(request.template(), request.params());
        long timeEpochSeconds = Instant.now().getEpochSecond();
        String cacheKey = "instant|" + request.template() + "|" + paramsCacheToken(request.params());

        MetricsQueryResponse response = (MetricsQueryResponse) cache.get(cacheKey,
            key -> mapInstantResponse(prometheusClient.instantQuery(promql, timeEpochSeconds)));
        auditLogger.logQuery(adminPrincipal, clientInfo, "query", request.template(), request.params(), null, null);

        return response;
    }

    @Override
    public MetricsQueryRangeResponse queryRange(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        MetricsQueryRangeRequest request = readRequest(requestNode, MetricsQueryRangeRequest.class);
        requireTemplate(request.template());
        ParsedTimeRange timeRange = parseTimeRange(request.timeRange());
        String step = validateStep(request.step());
        String promql = templateRegistry.buildPromql(request.template(), request.params());
        String cacheKey = "range|" + request.template() + "|" + paramsCacheToken(request.params()) + "|"
            + timeRange.fromSeconds() + "-" + timeRange.toSeconds() + "-" + step;

        MetricsQueryRangeResponse response = (MetricsQueryRangeResponse) cache.get(cacheKey, key -> mapRangeResponse(
            prometheusClient.rangeQuery(promql, timeRange.fromSeconds(), timeRange.toSeconds(), step), step));
        auditLogger.logQuery(adminPrincipal, clientInfo, "query-range", request.template(), request.params(),
            request.timeRange(), step);

        return response;
    }

    private MetricsQueryResponse mapInstantResponse(JsonNode response) {
        JsonNode data = response.path("data");
        String resultType = data.path("resultType").asText("");
        if (!RESULT_TYPE_VECTOR.equals(resultType) && !data.path("result").isArray()) {
            throw AdminMetricsException.upstream(null);
        }
        List<MetricsSeriesResponse> series = new ArrayList<>();
        JsonNode resultArray = data.path("result");
        if (resultArray.isArray()) {
            for (JsonNode entry : resultArray) {
                Map<String, String> labels = readLabels(entry.path("metric"));
                Double value = readPrometheusValueArray(entry.path("value"));
                series.add(new MetricsSeriesResponse(labels, value, null));
            }
        }

        return new MetricsQueryResponse(RESULT_TYPE_VECTOR, series);
    }

    private MetricsQueryRangeResponse mapRangeResponse(JsonNode response, String step) {
        JsonNode data = response.path("data");
        String resultType = data.path("resultType").asText("");
        if (!RESULT_TYPE_MATRIX.equals(resultType) && !data.path("result").isArray()) {
            throw AdminMetricsException.upstream(null);
        }
        List<MetricsSeriesResponse> series = new ArrayList<>();
        JsonNode resultArray = data.path("result");
        if (resultArray.isArray()) {
            for (JsonNode entry : resultArray) {
                Map<String, String> labels = readLabels(entry.path("metric"));
                List<MetricsSampleResponse> samples = new ArrayList<>();
                JsonNode valuesNode = entry.path("values");
                if (valuesNode.isArray()) {
                    for (JsonNode sample : valuesNode) {
                        if (!sample.isArray() || sample.size() < 2) {
                            continue;
                        }
                        String ts = formatTimestamp(sample.get(0));
                        Double value = parsePrometheusNumber(sample.get(1).asText(""));
                        samples.add(new MetricsSampleResponse(ts, value));
                    }
                }
                series.add(new MetricsSeriesResponse(labels, null, samples));
            }
        }

        return new MetricsQueryRangeResponse(RESULT_TYPE_MATRIX, step, series);
    }

    private Map<String, String> readLabels(JsonNode metricNode) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metricNode != null && metricNode.isObject()) {
            metricNode.fieldNames().forEachRemaining(name -> labels.put(name, metricNode.path(name).asText("")));
        }

        return labels;
    }

    private Double readPrometheusValueArray(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isArray() || valueNode.size() < 2) {
            return null;
        }

        return parsePrometheusNumber(valueNode.get(1).asText(""));
    }

    private Double parsePrometheusNumber(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        // Prometheus 는 +Inf / -Inf / NaN 을 문자열로 반환 — 모두 null 로 정규화
        String normalized = raw.trim();
        if ("NaN".equals(normalized) || "+Inf".equals(normalized) || "-Inf".equals(normalized)
            || "Inf".equals(normalized)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(normalized);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return null;
            }

            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatTimestamp(JsonNode tsNode) {
        if (tsNode == null) {
            return "";
        }
        double epochSeconds = tsNode.asDouble(0.0);
        long millis = Math.round(epochSeconds * 1000.0);

        return Instant.ofEpochMilli(millis).toString();
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw AdminMetricsException.unauthorized();
        }
    }

    private void requireTemplate(String templateId) {
        if (templateId == null || templateId.isBlank() || !templateRegistry.hasTemplate(templateId)) {
            throw AdminMetricsException.unknownTemplate();
        }
    }

    private <T> T readRequest(JsonNode requestNode, Class<T> type) {
        if (requestNode == null || !requestNode.isObject()) {
            throw AdminMetricsException.invalidQuery();
        }
        try {
            return objectMapper.treeToValue(requestNode, type);
        } catch (Exception e) {
            throw AdminMetricsException.invalidQuery();
        }
    }

    private ParsedTimeRange parseTimeRange(MetricsTimeRange timeRange) {
        if (timeRange == null || timeRange.from() == null || timeRange.to() == null) {
            throw AdminMetricsException.invalidTimeRange();
        }
        Instant from;
        Instant to;
        try {
            from = Instant.parse(timeRange.from());
            to = Instant.parse(timeRange.to());
        } catch (DateTimeParseException e) {
            throw AdminMetricsException.invalidTimeRange();
        }
        if (!from.isBefore(to)) {
            throw AdminMetricsException.invalidTimeRange();
        }
        if (Duration.between(from, to).compareTo(MAX_TIME_RANGE) > 0) {
            throw AdminMetricsException.invalidTimeRange();
        }

        return new ParsedTimeRange(from.getEpochSecond(), to.getEpochSecond());
    }

    private String validateStep(String step) {
        if (step == null || !MetricsTemplateRegistry.isValidStep(step)) {
            throw AdminMetricsException.invalidParam();
        }

        return step;
    }

    private String paramsCacheToken(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        // 순서 일관성을 위해 키 정렬
        Map<String, String> sorted = new TreeMap<>(params);

        return sorted.toString();
    }

    private record ParsedTimeRange(long fromSeconds, long toSeconds) {
    }
}
