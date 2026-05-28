package com.nemonicworld.backoffice.infinitecanvas.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasCloseResponse;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.backoffice.infinitecanvas.service.room.BackofficeInfiniteCanvasCommandUseCase;
import com.nemonicworld.backoffice.infinitecanvas.service.room.BackofficeInfiniteCanvasQueryUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class BackofficeInfiniteCanvasServiceImpl implements BackofficeInfiniteCanvasService {

    private final BackofficeInfiniteCanvasQueryUseCase backofficeInfiniteCanvasQueryUseCase;
    private final BackofficeInfiniteCanvasCommandUseCase backofficeInfiniteCanvasCommandUseCase;

    public BackofficeInfiniteCanvasServiceImpl(
        BackofficeInfiniteCanvasQueryUseCase backofficeInfiniteCanvasQueryUseCase,
        BackofficeInfiniteCanvasCommandUseCase backofficeInfiniteCanvasCommandUseCase) {
        this.backofficeInfiniteCanvasQueryUseCase = backofficeInfiniteCanvasQueryUseCase;
        this.backofficeInfiniteCanvasCommandUseCase = backofficeInfiniteCanvasCommandUseCase;
    }

    @Override
    public BackofficeInfiniteCanvasListResponse getActiveCanvases(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        return backofficeInfiniteCanvasQueryUseCase.getActiveCanvases(adminPrincipal, status, page, size);
    }

    @Override
    public BackofficeInfiniteCanvasCloseResponse closeActiveCanvas(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        return backofficeInfiniteCanvasCommandUseCase.closeActiveCanvas(adminPrincipal, roomCode, clientInfo);
    }
}
