package com.nemonicworld.backoffice.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.backoffice.metrics.service.query.AdminMetricsQueryUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsServiceImpl implements AdminMetricsService {

    private final AdminMetricsQueryUseCase adminMetricsQueryUseCase;

    public AdminMetricsServiceImpl(AdminMetricsQueryUseCase adminMetricsQueryUseCase) {
        this.adminMetricsQueryUseCase = adminMetricsQueryUseCase;
    }

    @Override
    public MetricsQueryResponse query(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo) {
        return adminMetricsQueryUseCase.query(adminPrincipal, requestNode, clientInfo);
    }

    @Override
    public MetricsQueryRangeResponse queryRange(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo) {
        return adminMetricsQueryUseCase.queryRange(adminPrincipal, requestNode, clientInfo);
    }
}
