package com.nemonicworld.backoffice.relay.service;

import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomDeleteResponse;
import com.nemonicworld.backoffice.relay.dto.response.BackofficeRelayRoomListResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface BackofficeRelayRoomService {

    BackofficeRelayRoomListResponse getActiveRelayRooms(AdminPrincipal adminPrincipal, String status, String page,
        String size);

    BackofficeRelayRoomDeleteResponse deleteActiveRelayRoom(AdminPrincipal adminPrincipal, String roomCode,
        AdminClientInfo clientInfo);
}
