package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AdminLogsService {

    LogsSearchResponse search(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo);

    LogsHistogramResponse histogram(AdminPrincipal adminPrincipal, JsonNode requestNode, AdminClientInfo clientInfo);

    LogsFieldSummaryResponse fieldSummary(AdminPrincipal adminPrincipal, JsonNode requestNode,
        AdminClientInfo clientInfo);
}
