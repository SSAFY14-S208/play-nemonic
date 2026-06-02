package com.nemonicworld.backoffice.relay.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomDeleteResponse;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomListResponse;
import com.nemonicworld.backoffice.relay.service.room.BackofficeRelayRoomCommandUseCase;
import com.nemonicworld.backoffice.relay.service.room.BackofficeRelayRoomQueryUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class BackofficeRelayRoomServiceImpl implements BackofficeRelayRoomService {

    private final BackofficeRelayRoomQueryUseCase backofficeRelayRoomQueryUseCase;
    private final BackofficeRelayRoomCommandUseCase backofficeRelayRoomCommandUseCase;

    public BackofficeRelayRoomServiceImpl(BackofficeRelayRoomQueryUseCase backofficeRelayRoomQueryUseCase,
        BackofficeRelayRoomCommandUseCase backofficeRelayRoomCommandUseCase) {
        this.backofficeRelayRoomQueryUseCase = backofficeRelayRoomQueryUseCase;
        this.backofficeRelayRoomCommandUseCase = backofficeRelayRoomCommandUseCase;
    }

    @Override
    public BackofficeRelayRoomListResponse getActiveRelayRooms(AdminPrincipal adminPrincipal, String status,
        String page, String size) {
        return backofficeRelayRoomQueryUseCase.getActiveRelayRooms(adminPrincipal, status, page, size);
    }

    @Override
    public BackofficeRelayRoomDeleteResponse deleteActiveRelayRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo) {
        return backofficeRelayRoomCommandUseCase.deleteActiveRelayRoom(adminPrincipal, roomCode, clientInfo);
    }
}
