package com.nemonicworld.invite.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.user.entity.AppUser;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 플립북 초대코드 입장 핸들러의 부스별 Redis 방 상태 검증을 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class FlipbookInviteJoinHandlerTest {

    private static final String INVITE_CODE = "FB3K9Q";
    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    /**
     * 플립북 방에서 강퇴된 UUID는 초대코드 입장 경로로도 재입장할 수 없습니다.
     */
    @Test
    void joinRejectsKickedFlipbookUser() {
        UUID hostUuid = UUID.randomUUID();
        UUID kickedUuid = UUID.randomUUID();
        FlipbookRoomState baseRoomState = waitingRoom(participant(hostUuid, "망고", true, 0));
        FlipbookRoomState roomState = baseRoomState.withParticipantsAndKickedUserUuids(baseRoomState.participants(),
            List.of(kickedUuid.toString()), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        FlipbookInviteJoinHandler handler = new FlipbookInviteJoinHandler(flipbookRoomRepository,
            flipbookInviteMetadataSyncService);

        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> handler.join(activeInvite(), user(kickedUuid, "포도")))
            .isInstanceOf(ForbiddenException.class).hasMessage("강퇴된 방에는 다시 입장할 수 없습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(flipbookInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    private InviteMetadata activeInvite() {
        return new InviteMetadata(INVITE_CODE, "flipbook", ROOM_CODE, "초대받은 플립북 방", LocalDateTime.now().plusHours(1));
    }

    private FlipbookRoomState waitingRoom(FlipbookRoomParticipant... participants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.WAITING, participants[0].userUuid(), 45, 2, 6,
            List.of(participants), now.minusMinutes(5), now);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null, now);
    }

    private AppUser user(UUID userUuid, String nickname) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser user = AppUser.createAnonymous(userUuid, "test-agent", now);
        user.updateNickname(nickname, now);

        return user;
    }
}
