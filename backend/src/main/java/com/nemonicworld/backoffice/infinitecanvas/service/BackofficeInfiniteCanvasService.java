package com.nemonicworld.backoffice.infinitecanvas.service;

import com.nemonicworld.backoffice.infinitecanvas.dto.response.BackofficeInfiniteCanvasListResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface BackofficeInfiniteCanvasService {

    BackofficeInfiniteCanvasListResponse getActiveCanvases(AdminPrincipal adminPrincipal, String status, String page,
        String size);
}
