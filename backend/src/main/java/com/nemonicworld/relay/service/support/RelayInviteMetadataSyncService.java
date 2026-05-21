package com.nemonicworld.relay.service.support;

import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 릴레이 방 상태 갱신 시 공통 초대코드 메타데이터의 만료 시각도 함께 맞추는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class RelayInviteMetadataSyncService {

    private static final String BOOTH_TYPE_RELAY = "relay";
    private static final String DEFAULT_ROOM_NAME_SUFFIX = "의 릴레이 드로잉";

    private final InviteRepository inviteRepository;

    /**
     * 방 Redis TTL이 다시 24시간으로 갱신된 뒤 invite:{roomCode}도 같은 생명주기로 다시 저장합니다.
     */
    public void syncWithRoomState(RelayRoomState roomState) {
        LocalDateTime expiresAt = resolveExpiresAt(roomState);
        Optional<InviteMetadata> inviteMetadata = inviteRepository.findByInviteCode(roomState.roomCode())
            .map(invite -> invite.withExpiresAt(expiresAt)).or(() -> createRestoredInvite(roomState, expiresAt));

        inviteMetadata.ifPresent(invite -> inviteRepository.save(invite, RelayRoomRepository.ROOM_STATE_TTL));
    }

    private Optional<InviteMetadata> createRestoredInvite(RelayRoomState roomState, LocalDateTime expiresAt) {
        return findHostNickname(roomState).map(hostNickname -> new InviteMetadata(roomState.roomCode(),
            BOOTH_TYPE_RELAY, roomState.roomCode(), hostNickname + DEFAULT_ROOM_NAME_SUFFIX, expiresAt));
    }

    private Optional<String> findHostNickname(RelayRoomState roomState) {
        return roomState.participants().stream()
            .filter(participant -> participant.host() || participant.userUuid().equals(roomState.hostUserUuid()))
            .map(RelayRoomParticipant::nickname).findFirst();
    }

    private LocalDateTime resolveExpiresAt(RelayRoomState roomState) {
        LocalDateTime baseTime = roomState.updatedAt() == null
            ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            : roomState.updatedAt();

        return baseTime.plus(RelayRoomRepository.ROOM_STATE_TTL);
    }
}
