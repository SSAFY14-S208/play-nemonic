package com.nemonicworld.backoffice.metrics.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsSampleResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsSeriesResponse;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminMetricsResponseMapper {

    private static final String RESULT_TYPE_VECTOR = "vector";
    private static final String RESULT_TYPE_MATRIX = "matrix";

    public MetricsQueryResponse mapInstantResponse(JsonNode response) {
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

    public MetricsQueryRangeResponse mapRangeResponse(JsonNode response, String step) {
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
}
