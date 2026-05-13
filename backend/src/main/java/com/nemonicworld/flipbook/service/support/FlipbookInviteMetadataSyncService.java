package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 플립북 방 상태 갱신 시 공통 초대코드 메타데이터의 만료 시각도 함께 맞추는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookInviteMetadataSyncService {

    private static final String BOOTH_TYPE_FLIPBOOK = "flipbook";
    private static final String DEFAULT_ROOM_NAME_SUFFIX = "의 플립북";

    private final InviteRepository inviteRepository;

    /**
     * 방 Redis TTL이 다시 24시간으로 갱신된 뒤 invite:{roomCode}도 같은 생명주기로 다시 저장합니다.
     */
    public void syncWithRoomState(FlipbookRoomState roomState) {
        LocalDateTime expiresAt = resolveExpiresAt(roomState);
        Optional<InviteMetadata> inviteMetadata = inviteRepository.findByInviteCode(roomState.roomCode())
            .map(invite -> invite.withExpiresAt(expiresAt)).or(() -> createRestoredInvite(roomState, expiresAt));

        inviteMetadata.ifPresent(invite -> inviteRepository.save(invite, FlipbookRoomRepository.ROOM_STATE_TTL));
    }

    private Optional<InviteMetadata> createRestoredInvite(FlipbookRoomState roomState, LocalDateTime expiresAt) {
        return findHostNickname(roomState).map(hostNickname -> new InviteMetadata(roomState.roomCode(),
            BOOTH_TYPE_FLIPBOOK, roomState.roomCode(), hostNickname + DEFAULT_ROOM_NAME_SUFFIX, expiresAt));
    }

    private Optional<String> findHostNickname(FlipbookRoomState roomState) {
        return roomState.participants().stream()
            .filter(participant -> participant.host() || participant.userUuid().equals(roomState.hostUserUuid()))
            .map(FlipbookRoomParticipant::nickname).findFirst();
    }

    private LocalDateTime resolveExpiresAt(FlipbookRoomState roomState) {
        LocalDateTime baseTime = roomState.updatedAt() == null
            ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            : roomState.updatedAt();

        return baseTime.plus(FlipbookRoomRepository.ROOM_STATE_TTL);
    }
}
