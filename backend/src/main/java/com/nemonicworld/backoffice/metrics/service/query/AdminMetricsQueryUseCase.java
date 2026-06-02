package com.nemonicworld.backoffice.metrics.service.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsQueryRangeRequest;
import com.nemonicworld.backoffice.metrics.dto.request.MetricsQueryRequest;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import com.nemonicworld.backoffice.metrics.service.MetricsAuditLogger;
import com.nemonicworld.backoffice.metrics.service.MetricsTemplateRegistry;
import com.nemonicworld.backoffice.metrics.service.PrometheusClient;
import com.nemonicworld.backoffice.metrics.service.support.AdminMetricsRequestReader;
import com.nemonicworld.backoffice.metrics.service.support.AdminMetricsResponseMapper;
import com.nemonicworld.backoffice.metrics.service.support.AdminMetricsValidationSupport;
import com.nemonicworld.backoffice.metrics.service.support.AdminMetricsValidationSupport.ParsedTimeRange;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsQueryUseCase {

    private final AdminMetricsRequestReader requestReader;
    private final AdminMetricsResponseMapper responseMapper;
    private final AdminMetricsValidationSupport validationSupport;
    private final MetricsTemplateRegistry templateRegistry;
    private final PrometheusClient prometheusClient;
    private final MetricsAuditLogger auditLogger;
    private final Cache<String, Object> cache;

    public AdminMetricsQueryUseCase(AdminMetricsRequestReader requestReader, AdminMetricsResponseMapper responseMapper,
        AdminMetricsValidationSupport validationSupport, MetricsTemplateRegistry templateRegistry,
        PrometheusClient prometheusClient, MetricsAuditLogger auditLogger, Cache<String, Object> adminMetricsCache) {
        this.requestReader = requestReader;
        this.responseMapper = responseMapper;
        this.validationSupport = validationSupport;
        this.templateRegistry = templateRegistry;
        this.prometheusClient = prometheusClient;
        this.auditLogger = auditLogger;
        this.cache = adminMetricsCache;
    }

    public MetricsQueryResponse query(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        MetricsQueryRequest request = requestReader.readRequest(requestNode, MetricsQueryRequest.class);
        requireTemplate(request.template());
        String promql = templateRegistry.buildPromql(request.template(), request.params());
        long timeEpochSeconds = Instant.now().getEpochSecond();
        String cacheKey = "instant|" + request.template() + "|" + paramsCacheToken(request.params());

        MetricsQueryResponse response = (MetricsQueryResponse) cache.get(cacheKey,
            key -> responseMapper.mapInstantResponse(prometheusClient.instantQuery(promql, timeEpochSeconds)));
        auditLogger.logQuery(adminPrincipal, clientInfo, "query", request.template(), request.params(), null, null);

        return response;
    }

    public MetricsQueryRangeResponse queryRange(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        MetricsQueryRangeRequest request = requestReader.readRequest(requestNode, MetricsQueryRangeRequest.class);
        requireTemplate(request.template());
        ParsedTimeRange timeRange = validationSupport.parseTimeRange(request.timeRange());
        String step = validationSupport.validateStep(request.step());
        String promql = templateRegistry.buildPromql(request.template(), request.params());
        String cacheKey = "range|" + request.template() + "|" + paramsCacheToken(request.params()) + "|"
            + timeRange.fromSeconds() + "-" + timeRange.toSeconds() + "-" + step;

        MetricsQueryRangeResponse response = (MetricsQueryRangeResponse) cache.get(cacheKey,
            key -> responseMapper.mapRangeResponse(
                prometheusClient.rangeQuery(promql, timeRange.fromSeconds(), timeRange.toSeconds(), step), step));
        auditLogger.logQuery(adminPrincipal, clientInfo, "query-range", request.template(), request.params(),
            request.timeRange(), step);

        return response;
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

    private String paramsCacheToken(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        Map<String, String> sorted = new TreeMap<>(params);

        return sorted.toString();
    }
}
