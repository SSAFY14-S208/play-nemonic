package com.nemonicworld.backoffice.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AdminMetricsService {

    MetricsQueryResponse query(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo);

    MetricsQueryRangeResponse queryRange(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo);
}
