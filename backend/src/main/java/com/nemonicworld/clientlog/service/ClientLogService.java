package com.nemonicworld.clientlog.service;

import com.nemonicworld.clientlog.dto.request.ClientLogIngestRequest;
import com.nemonicworld.clientlog.dto.response.ClientLogIngestResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface ClientLogService {

    ClientLogIngestResponse ingest(ClientLogIngestRequest request, HttpServletRequest servletRequest);
}
