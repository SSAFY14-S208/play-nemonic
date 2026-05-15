package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.logs.dto.request.LogsFieldSummaryRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsHistogramRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsSearchRequest;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryItemResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramBucketResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchHitResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminLogsServiceImpl implements AdminLogsService {

    private static final TypeReference<Map<String, Object>> SOURCE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> SORT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final LogsQueryBuilder queryBuilder;
    private final OpenSearchClient openSearchClient;
    private final LogsAuditLogger auditLogger;

    public AdminLogsServiceImpl(ObjectMapper objectMapper, LogsQueryBuilder queryBuilder,
        OpenSearchClient openSearchClient, LogsAuditLogger auditLogger) {
        this.objectMapper = objectMapper;
        this.queryBuilder = queryBuilder;
        this.openSearchClient = openSearchClient;
        this.auditLogger = auditLogger;
    }

    @Override
    public LogsSearchResponse search(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsSearchRequest request = readRequest(requestNode, LogsSearchRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildSearchQuery(request);
        LogsSearchResponse response = parseSearchResponse(openSearchClient.search(query.indexPattern(), query.body()));
        auditLogger.logQuery(adminPrincipal, clientInfo, "search", request.index(), request.query(),
            request.timeRange(), response.total(), response.tookMs());

        return response;
    }

    @Override
    public LogsHistogramResponse histogram(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsHistogramRequest request = readRequest(requestNode, LogsHistogramRequest.class);
        String interval = queryBuilder.chooseHistogramInterval(request.timeRange());
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildHistogramQuery(request, interval);
        ParsedHistogramResponse parsed = parseHistogramResponse(
            openSearchClient.search(query.indexPattern(), query.body()), interval);
        auditLogger.logQuery(adminPrincipal, clientInfo, "histogram", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    @Override
    public LogsFieldSummaryResponse fieldSummary(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsFieldSummaryRequest request = readRequest(requestNode, LogsFieldSummaryRequest.class);
        LogsQueryBuilder.BuiltMsearchQuery query = queryBuilder.buildFieldSummaryQuery(request);
        ParsedFieldSummaryResponse parsed = parseFieldSummaryResponse(openSearchClient.msearch(query.body()),
            query.fields());
        auditLogger.logQuery(adminPrincipal, clientInfo, "field-summary", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw AdminLogsException.unauthorized();
        }
    }

    private <T> T readRequest(JsonNode requestNode, Class<T> type) {
        try {
            return objectMapper.treeToValue(requestNode, type);
        } catch (Exception e) {
            throw AdminLogsException.invalidQuery();
        }
    }

    private LogsSearchResponse parseSearchResponse(JsonNode response) {
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

    private ParsedHistogramResponse parseHistogramResponse(JsonNode response, String interval) {
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
                buckets.add(new LogsHistogramBucketResponse(bucketTimestamp(bucket),
                    bucket.path("doc_count").asLong(0L), byLevel));
            }
        }

        return new ParsedHistogramResponse(new LogsHistogramResponse(interval, buckets), total, tookMs);
    }

    private ParsedFieldSummaryResponse parseFieldSummaryResponse(JsonNode response, List<String> fields) {
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

    private record ParsedHistogramResponse(LogsHistogramResponse response, long total, long tookMs) {
    }

    private record ParsedFieldSummaryResponse(LogsFieldSummaryResponse response, long total, long tookMs) {
    }
}
