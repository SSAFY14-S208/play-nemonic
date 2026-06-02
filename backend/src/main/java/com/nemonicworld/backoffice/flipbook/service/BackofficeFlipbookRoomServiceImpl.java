package com.nemonicworld.backoffice.flipbook.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomDeleteResponse;
import com.nemonicworld.backoffice.flipbook.dto.response.BackofficeFlipbookRoomListResponse;
import com.nemonicworld.backoffice.flipbook.service.room.BackofficeFlipbookRoomCommandUseCase;
import com.nemonicworld.backoffice.flipbook.service.room.BackofficeFlipbookRoomQueryUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class BackofficeFlipbookRoomServiceImpl implements BackofficeFlipbookRoomService {

    private final BackofficeFlipbookRoomQueryUseCase backofficeFlipbookRoomQueryUseCase;
    private final BackofficeFlipbookRoomCommandUseCase backofficeFlipbookRoomCommandUseCase;

    public BackofficeFlipbookRoomServiceImpl(BackofficeFlipbookRoomQueryUseCase backofficeFlipbookRoomQueryUseCase,
        BackofficeFlipbookRoomCommandUseCase backofficeFlipbookRoomCommandUseCase) {
        this.backofficeFlipbookRoomQueryUseCase = backofficeFlipbookRoomQueryUseCase;
        this.backofficeFlipbookRoomCommandUseCase = backofficeFlipbookRoomCommandUseCase;
    }

    @Override
    public BackofficeFlipbookRoomListResponse getActiveFlipbookRooms(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        return backofficeFlipbookRoomQueryUseCase.getActiveFlipbookRooms(adminPrincipal, status, page, size);
    }

    @Override
    public BackofficeFlipbookRoomDeleteResponse deleteActiveFlipbookRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        return backofficeFlipbookRoomCommandUseCase.deleteActiveFlipbookRoom(adminPrincipal, roomCode, clientInfo);
    }
}
