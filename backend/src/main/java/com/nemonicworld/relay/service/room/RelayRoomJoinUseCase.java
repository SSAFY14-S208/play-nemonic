package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 방 입장과 재입장 유스케이스입니다.
 */
@Service
public class RelayRoomJoinUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    public RelayRoomJoinUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomRepository relayRoomRepository,
        RelayRoomPolicy relayRoomPolicy, RelayRoomViewerFactory relayRoomViewerFactory,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
    }

    /**
     * 릴레이 방 입장 또는 재입장을 처리합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse joinRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            String viewerUserUuid = viewerUser.getId().toString();
            relayRoomPolicy.validateNotKicked(roomState, viewerUserUuid);
            relayRoomPolicy.validateNotDropped(roomState, viewerUserUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Optional<RelayRoomParticipant> participant = relayRoomPolicy.findParticipant(roomState, viewerUserUuid);

            if (participant.isPresent()) {
                Optional<RelayRoomStateResponse> existingParticipantResponse = joinExistingParticipant(viewerUserUuid,
                    roomState, participant.get(), now);

                if (existingParticipantResponse.isPresent()) {
                    return existingParticipantResponse.get();
                }

                continue;
            }

            Optional<RelayRoomStateResponse> joinResponse = joinNewParticipant(viewerUser, roomState, now);

            if (joinResponse.isPresent()) {
                return joinResponse.get();
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 기존 참여자의 입장 재호출을 처리합니다.
     */
    private Optional<RelayRoomStateResponse> joinExistingParticipant(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        if (participant.connected()) {
            RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, roomState, now);

            return Optional.of(RelayRoomStateResponse.from(roomState, viewer));
        }

        if (participant.disconnectedAt() != null && relayRoomPolicy.requiresReconnectGrace(roomState)) {
            relayRoomPolicy.requireReconnectable(participant, now);
        }

        RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, roomState, now);

        return Optional.of(RelayRoomStateResponse.from(roomState, viewer));
    }

    /**
     * 새 참여자를 방에 추가합니다.
     */
    private Optional<RelayRoomStateResponse> joinNewParticipant(AppUser viewerUser, RelayRoomState roomState,
        LocalDateTime now) {
        relayRoomPolicy.validateJoinableRoom(roomState);
        relayRoomPolicy.validateNicknameRegistered(viewerUser);

        RelayRoomParticipant newParticipant = new RelayRoomParticipant(viewerUser.getId().toString(),
            viewerUser.getNickname(), false, relayRoomPolicy.nextJoinOrder(roomState), false, null, now);
        List<RelayRoomParticipant> participants = new ArrayList<>(roomState.participants());
        participants.add(newParticipant);
        RelayRoomState updatedRoomState = roomState.withParticipants(participants, now);

        if (!relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
            return Optional.empty();
        }

        relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
        RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUser.getId().toString(), updatedRoomState,
            now);

        return Optional.of(RelayRoomStateResponse.from(updatedRoomState, viewer));
    }

}
