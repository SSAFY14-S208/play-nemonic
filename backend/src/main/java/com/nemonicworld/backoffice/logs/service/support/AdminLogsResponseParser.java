package com.nemonicworld.backoffice.logs.service.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilteredMetricsRequest;
import com.nemonicworld.backoffice.logs.dto.response.LogsCompositeBucketResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsCompositeBucketsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsDistinctCountResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryItemResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFilteredMetricsGroupResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFilteredMetricsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramBucketResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchHitResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithMetricBucketResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithMetricResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithSubsBucketResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithSubsResponse;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminLogsResponseParser {

    private static final TypeReference<Map<String, Object>> SOURCE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> SORT_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> KEY_MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AdminLogsResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LogsSearchResponse parseSearchResponse(JsonNode response) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        List<LogsSearchHitResponse> hits = new ArrayList<>();
        JsonNode hitNodes = response.path("hits").path("hits");
        if (hitNodes.isArray()) {
            for (JsonNode hit : hitNodes) {
                Map<String, Object> source = hit.path("_source").isObject()
                    ? objectMapper.convertValue(hit.path("_source"), SOURCE_TYPE)
                    : Map.of();
                hits.add(new LogsSearchHitResponse(hit.path("_id").asText(), hit.path("_index").asText(), source));
            }
        }

        List<Object> nextSearchAfter = null;
        if (!hits.isEmpty() && hitNodes.isArray()) {
            JsonNode sort = hitNodes.get(hitNodes.size() - 1).path("sort");
            if (sort.isArray()) {
                nextSearchAfter = objectMapper.convertValue(sort, SORT_TYPE);
            }
        }

        return new LogsSearchResponse(total, tookMs, hits, nextSearchAfter);
    }

    public ParsedHistogramResponse parseHistogramResponse(JsonNode response, String interval, boolean includeByField) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        List<LogsHistogramBucketResponse> buckets = new ArrayList<>();
        JsonNode bucketNodes = response.path("aggregations").path("ts").path("buckets");
        if (bucketNodes.isArray()) {
            for (JsonNode bucket : bucketNodes) {
                Map<String, Long> byLevel = new LinkedHashMap<>();
                JsonNode levelBuckets = bucket.path("level").path("buckets");
                if (levelBuckets.isArray()) {
                    for (JsonNode levelBucket : levelBuckets) {
                        byLevel.put(bucketKey(levelBucket), levelBucket.path("doc_count").asLong(0L));
                    }
                }
                Map<String, Long> byField = null;
                if (includeByField) {
                    byField = new LinkedHashMap<>();
                    JsonNode fieldBuckets = bucket.path("by_field").path("buckets");
                    if (fieldBuckets.isArray()) {
                        for (JsonNode fieldBucket : fieldBuckets) {
                            byField.put(bucketKey(fieldBucket), fieldBucket.path("doc_count").asLong(0L));
                        }
                    }
                }
                buckets.add(new LogsHistogramBucketResponse(bucketTimestamp(bucket),
                    bucket.path("doc_count").asLong(0L), byLevel, byField));
            }
        }

        return new ParsedHistogramResponse(new LogsHistogramResponse(interval, buckets), total, tookMs);
    }

    public ParsedFieldSummaryResponse parseFieldSummaryResponse(JsonNode response, List<String> fields) {
        Map<String, List<LogsFieldSummaryItemResponse>> summaries = new LinkedHashMap<>();
        JsonNode responses = response.path("responses");
        long total = 0L;
        long tookMs = 0L;
        for (int index = 0; index < fields.size(); index++) {
            JsonNode itemResponse = responses.isArray() && responses.size() > index ? responses.get(index) : null;
            if (itemResponse == null || itemResponse.has("error")) {
                throw AdminLogsException.upstream(null);
            }
            if (index == 0) {
                total = parseTotal(itemResponse);
            }
            tookMs += itemResponse.path("took").asLong(0L);
            List<LogsFieldSummaryItemResponse> items = new ArrayList<>();
            JsonNode buckets = itemResponse.path("aggregations").path("values").path("buckets");
            if (buckets.isArray()) {
                for (JsonNode bucket : buckets) {
                    items.add(new LogsFieldSummaryItemResponse(bucketKey(bucket), bucket.path("doc_count").asLong(0L)));
                }
            }
            summaries.put(fields.get(index), items);
        }

        return new ParsedFieldSummaryResponse(new LogsFieldSummaryResponse(summaries), total, tookMs);
    }

    public ParsedTermsWithSubsResponse parseTermsWithSubsResponse(JsonNode response, List<String> subFilterNames) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        List<LogsTermsWithSubsBucketResponse> buckets = new ArrayList<>();
        JsonNode bucketNodes = response.path("aggregations").path("groups").path("buckets");
        if (bucketNodes.isArray()) {
            for (JsonNode bucket : bucketNodes) {
                Map<String, Long> sub = new LinkedHashMap<>();
                for (String name : subFilterNames) {
                    sub.put(name, bucket.path(name).path("doc_count").asLong(0L));
                }
                buckets.add(
                    new LogsTermsWithSubsBucketResponse(bucketKey(bucket), bucket.path("doc_count").asLong(0L), sub));
            }
        }
        buckets.sort(Comparator.comparingLong(LogsTermsWithSubsBucketResponse::total).reversed());

        return new ParsedTermsWithSubsResponse(new LogsTermsWithSubsResponse(buckets), total, tookMs);
    }

    public ParsedTermsWithMetricResponse parseTermsWithMetricResponse(JsonNode response, List<String> metricNames) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        List<LogsTermsWithMetricBucketResponse> buckets = new ArrayList<>();
        JsonNode bucketNodes = response.path("aggregations").path("groups").path("buckets");
        if (bucketNodes.isArray()) {
            for (JsonNode bucket : bucketNodes) {
                Map<String, Double> metrics = new LinkedHashMap<>();
                for (String name : metricNames) {
                    metrics.put(name, readMetricValue(bucket.path(name)));
                }
                buckets.add(new LogsTermsWithMetricBucketResponse(bucketKey(bucket),
                    bucket.path("doc_count").asLong(0L), metrics));
            }
        }
        String primaryMetric = metricNames.isEmpty() ? null : metricNames.get(0);
        if (primaryMetric != null) {
            buckets.sort(Comparator.comparingDouble((LogsTermsWithMetricBucketResponse bucket) -> {
                Double value = bucket.metrics().get(primaryMetric);
                return value == null ? 0.0 : value;
            }).reversed());
        }

        return new ParsedTermsWithMetricResponse(new LogsTermsWithMetricResponse(buckets), total, tookMs);
    }

    public ParsedCompositeBucketsResponse parseCompositeBucketsResponse(JsonNode response, List<String> subAggNames) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        List<LogsCompositeBucketResponse> buckets = new ArrayList<>();
        JsonNode compositeNode = response.path("aggregations").path("composite_buckets");
        JsonNode bucketNodes = compositeNode.path("buckets");
        if (bucketNodes.isArray()) {
            for (JsonNode bucket : bucketNodes) {
                Map<String, Object> keys = bucket.path("key").isObject()
                    ? objectMapper.convertValue(bucket.path("key"), KEY_MAP_TYPE)
                    : Map.of();
                Map<String, Double> sub = null;
                if (!subAggNames.isEmpty()) {
                    sub = new LinkedHashMap<>();
                    for (String name : subAggNames) {
                        sub.put(name, readMetricValue(bucket.path(name)));
                    }
                }
                buckets.add(new LogsCompositeBucketResponse(keys, bucket.path("doc_count").asLong(0L), sub));
            }
        }
        Map<String, Object> afterKey = null;
        JsonNode afterKeyNode = compositeNode.path("after_key");
        if (afterKeyNode.isObject()) {
            afterKey = objectMapper.convertValue(afterKeyNode, KEY_MAP_TYPE);
        }

        return new ParsedCompositeBucketsResponse(new LogsCompositeBucketsResponse(buckets, afterKey), total, tookMs);
    }

    public ParsedFilteredMetricsResponse parseFilteredMetricsResponse(JsonNode response,
        LogsFilteredMetricsRequest request) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        Map<String, LogsFilteredMetricsGroupResponse> groups = new LinkedHashMap<>();
        JsonNode aggregations = response.path("aggregations");
        request.groups().forEach(group -> {
            JsonNode groupNode = aggregations.path(group.name());
            long count = groupNode.path("doc_count").asLong(0L);
            Double metric = group.metric() == null ? null : readMetricValue(groupNode.path("metric"));
            groups.put(group.name(), new LogsFilteredMetricsGroupResponse(count, metric));
        });

        return new ParsedFilteredMetricsResponse(new LogsFilteredMetricsResponse(groups), total, tookMs);
    }

    public ParsedDistinctCountResponse parseDistinctCountResponse(JsonNode response) {
        long total = parseTotal(response);
        long tookMs = response.path("took").asLong(0L);
        long value = response.path("aggregations").path("distinct").path("value").asLong(0L);

        return new ParsedDistinctCountResponse(new LogsDistinctCountResponse(value), total, tookMs);
    }

    private Double readMetricValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.0;
        }
        JsonNode value = node.path("value");
        if (value.isMissingNode() || value.isNull()) {
            return 0.0;
        }
        return value.asDouble(0.0);
    }

    private long parseTotal(JsonNode response) {
        JsonNode total = response.path("hits").path("total");
        if (total.isNumber()) {
            return total.asLong(0L);
        }

        return total.path("value").asLong(0L);
    }

    private String bucketKey(JsonNode bucket) {
        JsonNode key = bucket.get("key_as_string");
        if (key == null || key.isMissingNode()) {
            key = bucket.get("key");
        }

        if (key == null || key.isNull()) {
            return "UNKNOWN";
        }

        return key.isTextual() ? key.asText() : key.toString();
    }

    private String bucketTimestamp(JsonNode bucket) {
        JsonNode keyAsString = bucket.get("key_as_string");
        if (keyAsString != null && keyAsString.isTextual()) {
            try {
                return Instant.parse(keyAsString.asText()).toString();
            } catch (DateTimeParseException e) {
                return keyAsString.asText();
            }
        }

        JsonNode key = bucket.get("key");
        return key != null && key.isNumber() ? Instant.ofEpochMilli(key.asLong()).toString() : "";
    }

    public record ParsedHistogramResponse(LogsHistogramResponse response, long total, long tookMs) {
    }

    public record ParsedFieldSummaryResponse(LogsFieldSummaryResponse response, long total, long tookMs) {
    }

    public record ParsedTermsWithSubsResponse(LogsTermsWithSubsResponse response, long total, long tookMs) {
    }

    public record ParsedTermsWithMetricResponse(LogsTermsWithMetricResponse response, long total, long tookMs) {
    }

    public record ParsedCompositeBucketsResponse(LogsCompositeBucketsResponse response, long total, long tookMs) {
    }

    public record ParsedFilteredMetricsResponse(LogsFilteredMetricsResponse response, long total, long tookMs) {
    }

    public record ParsedDistinctCountResponse(LogsDistinctCountResponse response, long total, long tookMs) {
    }
}
