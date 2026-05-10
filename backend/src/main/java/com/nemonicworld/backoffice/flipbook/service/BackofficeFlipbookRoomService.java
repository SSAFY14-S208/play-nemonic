package com.nemonicworld.backoffice.flipbook.service;

import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomListResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface BackofficeFlipbookRoomService {

    BackofficeFlipbookRoomListResponse getActiveFlipbookRooms(AdminPrincipal adminPrincipal, String status, String page,
        String size);
}
