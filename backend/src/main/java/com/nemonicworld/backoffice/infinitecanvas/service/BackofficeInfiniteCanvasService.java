package com.nemonicworld.backoffice.infinitecanvas.service;

import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasCloseResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface BackofficeInfiniteCanvasService {

    BackofficeInfiniteCanvasListResponse getActiveCanvases(AdminPrincipal adminPrincipal, String status, String page,
        String size);

    BackofficeInfiniteCanvasCloseResponse closeActiveCanvas(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo);
}
