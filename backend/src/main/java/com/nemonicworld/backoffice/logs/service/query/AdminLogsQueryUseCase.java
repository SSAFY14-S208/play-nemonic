package com.nemonicworld.backoffice.logs.service.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.logs.dto.request.LogsCompositeBucketsRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsDistinctCountRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFieldSummaryRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilteredMetricsRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsHistogramRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsMetricSpec;
import com.nemonicworld.backoffice.logs.dto.request.LogsSearchRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsSubFilter;
import com.nemonicworld.backoffice.logs.dto.request.LogsTermsWithMetricRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsTermsWithSubsRequest;
import com.nemonicworld.backoffice.logs.dto.response.LogsCompositeBucketsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsDistinctCountResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFilteredMetricsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithMetricResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithSubsResponse;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import com.nemonicworld.backoffice.logs.service.LogsAuditLogger;
import com.nemonicworld.backoffice.logs.service.LogsQueryBuilder;
import com.nemonicworld.backoffice.logs.service.OpenSearchClient;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsRequestReader;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedCompositeBucketsResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedDistinctCountResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedFilteredMetricsResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedHistogramResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedTermsWithMetricResponse;
import com.nemonicworld.backoffice.logs.service.support.AdminLogsResponseParser.ParsedTermsWithSubsResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminLogsQueryUseCase {

    private final AdminLogsRequestReader requestReader;
    private final AdminLogsResponseParser responseParser;
    private final LogsQueryBuilder queryBuilder;
    private final OpenSearchClient openSearchClient;
    private final LogsAuditLogger auditLogger;

    public AdminLogsQueryUseCase(AdminLogsRequestReader requestReader, AdminLogsResponseParser responseParser,
        LogsQueryBuilder queryBuilder, OpenSearchClient openSearchClient, LogsAuditLogger auditLogger) {
        this.requestReader = requestReader;
        this.responseParser = responseParser;
        this.queryBuilder = queryBuilder;
        this.openSearchClient = openSearchClient;
        this.auditLogger = auditLogger;
    }

    public LogsSearchResponse search(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsSearchRequest request = requestReader.readRequest(requestNode, LogsSearchRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildSearchQuery(request);
        LogsSearchResponse response = responseParser
            .parseSearchResponse(openSearchClient.search(query.indexPattern(), query.body()));
        auditLogger.logQuery(adminPrincipal, clientInfo, "search", request.index(), request.query(),
            request.timeRange(), response.total(), response.tookMs());

        return response;
    }

    public LogsHistogramResponse histogram(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsHistogramRequest request = requestReader.readRequest(requestNode, LogsHistogramRequest.class);
        String interval = queryBuilder.chooseHistogramInterval(request.timeRange());
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildHistogramQuery(request, interval);
        boolean includeByField = request.groupBy() != null && !request.groupBy().isBlank();
        ParsedHistogramResponse parsed = responseParser.parseHistogramResponse(
            openSearchClient.search(query.indexPattern(), query.body()), interval, includeByField);
        auditLogger.logQuery(adminPrincipal, clientInfo, "histogram", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsFieldSummaryResponse fieldSummary(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsFieldSummaryRequest request = requestReader.readRequest(requestNode, LogsFieldSummaryRequest.class);
        LogsQueryBuilder.BuiltMsearchQuery query = queryBuilder.buildFieldSummaryQuery(request);
        ParsedFieldSummaryResponse parsed = responseParser
            .parseFieldSummaryResponse(openSearchClient.msearch(query.body()), query.fields());
        auditLogger.logQuery(adminPrincipal, clientInfo, "field-summary", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsTermsWithSubsResponse termsWithSubs(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsTermsWithSubsRequest request = requestReader.readRequest(requestNode, LogsTermsWithSubsRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildTermsWithSubsQuery(request);
        ParsedTermsWithSubsResponse parsed = responseParser.parseTermsWithSubsResponse(
            openSearchClient.search(query.indexPattern(), query.body()),
            request.subFilters().stream().map(LogsSubFilter::name).toList());
        auditLogger.logQuery(adminPrincipal, clientInfo, "terms-with-subs", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsTermsWithMetricResponse termsWithMetric(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsTermsWithMetricRequest request = requestReader.readRequest(requestNode, LogsTermsWithMetricRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildTermsWithMetricQuery(request);
        List<String> metricNames = request.metrics().stream().map(LogsMetricSpec::name).toList();
        ParsedTermsWithMetricResponse parsed = responseParser
            .parseTermsWithMetricResponse(openSearchClient.search(query.indexPattern(), query.body()), metricNames);
        auditLogger.logQuery(adminPrincipal, clientInfo, "terms-with-metric", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsCompositeBucketsResponse compositeBuckets(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsCompositeBucketsRequest request = requestReader.readRequest(requestNode, LogsCompositeBucketsRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildCompositeBucketsQuery(request);
        List<String> subAggNames = request.subAggs() == null
            ? List.of()
            : request.subAggs().stream().map(LogsMetricSpec::name).toList();
        ParsedCompositeBucketsResponse parsed = responseParser
            .parseCompositeBucketsResponse(openSearchClient.search(query.indexPattern(), query.body()), subAggNames);
        auditLogger.logQuery(adminPrincipal, clientInfo, "composite-buckets", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsFilteredMetricsResponse filteredMetrics(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsFilteredMetricsRequest request = requestReader.readRequest(requestNode, LogsFilteredMetricsRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildFilteredMetricsQuery(request);
        ParsedFilteredMetricsResponse parsed = responseParser
            .parseFilteredMetricsResponse(openSearchClient.search(query.indexPattern(), query.body()), request);
        auditLogger.logQuery(adminPrincipal, clientInfo, "filtered-metrics", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    public LogsDistinctCountResponse distinctCount(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        queryBuilder.validateRawRequest(requestNode);
        LogsDistinctCountRequest request = requestReader.readRequest(requestNode, LogsDistinctCountRequest.class);
        LogsQueryBuilder.BuiltLogsQuery query = queryBuilder.buildDistinctCountQuery(request);
        ParsedDistinctCountResponse parsed = responseParser
            .parseDistinctCountResponse(openSearchClient.search(query.indexPattern(), query.body()));
        auditLogger.logQuery(adminPrincipal, clientInfo, "distinct-count", request.index(), request.query(),
            request.timeRange(), parsed.total(), parsed.tookMs());

        return parsed.response();
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw AdminLogsException.unauthorized();
        }
    }
}
