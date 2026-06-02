package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.logs.dto.response.LogsCompositeBucketsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsDistinctCountResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFilteredMetricsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithMetricResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithSubsResponse;
import com.nemonicworld.backoffice.logs.service.query.AdminLogsQueryUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AdminLogsServiceImpl implements AdminLogsService {

    private final AdminLogsQueryUseCase adminLogsQueryUseCase;

    public AdminLogsServiceImpl(AdminLogsQueryUseCase adminLogsQueryUseCase) {
        this.adminLogsQueryUseCase = adminLogsQueryUseCase;
    }

    @Override
    public LogsSearchResponse search(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.search(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsHistogramResponse histogram(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.histogram(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsFieldSummaryResponse fieldSummary(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.fieldSummary(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsTermsWithSubsResponse termsWithSubs(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.termsWithSubs(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsTermsWithMetricResponse termsWithMetric(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.termsWithMetric(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsCompositeBucketsResponse compositeBuckets(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.compositeBuckets(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsFilteredMetricsResponse filteredMetrics(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.filteredMetrics(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public LogsDistinctCountResponse distinctCount(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminLogsQueryUseCase.distinctCount(adminPrincipal, requestNode, clientInfo);
    }
}
