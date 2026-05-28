package com.nemonicworld.clientlog.service;

import com.nemonicworld.clientlog.dto.request.ClientLogIngestRequest;
import com.nemonicworld.clientlog.dto.response.ClientLogIngestResponse;
import com.nemonicworld.clientlog.service.ingest.ClientLogIngestUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ClientLogServiceImpl implements ClientLogService {

    private final ClientLogIngestUseCase clientLogIngestUseCase;

    public ClientLogServiceImpl(ClientLogIngestUseCase clientLogIngestUseCase) {
        this.clientLogIngestUseCase = clientLogIngestUseCase;
    }

    @Override
    public ClientLogIngestResponse ingest(ClientLogIngestRequest request, HttpServletRequest servletRequest) {
        return clientLogIngestUseCase.ingest(request, servletRequest);
    }
}
