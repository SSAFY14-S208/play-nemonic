package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 릴레이 방 서비스의 Redis 낙관적 갱신 재시도 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RelayRoomServiceImplTest {

    private static final String ROOM_CODE = "AB3K9Q";

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @InjectMocks
    private RelayRoomServiceImpl relayRoomService;

    /**
     * 신규 입장 저장 중 충돌이 나면 최신 방 상태를 다시 읽고 다음 joinOrder로 재시도합니다.
     */
    @Test
    void joinRoomRetriesOptimisticSaveConflictWithLatestRoomState() {
        UUID hostUuid = UUID.randomUUID();
        UUID otherJoinerUuid = UUID.randomUUID();
        UUID joinerUuid = UUID.randomUUID();
        AppUser joiner = appUserWithNickname(joinerUuid, "포도");
        RelayRoomState firstReadRoomState = roomState(participant(hostUuid, "망고", true, 0));
        RelayRoomState secondReadRoomState = roomState(participant(hostUuid, "망고", true, 0),
            participant(otherJoinerUuid, "사과", false, 1));
        given(anonymousUserResolver.resolve(joinerUuid.toString())).willReturn(joiner);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstReadRoomState),
            Optional.of(secondReadRoomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomStateResponse response = relayRoomService.joinRoom(joinerUuid.toString(), ROOM_CODE);

        assertThat(response.participantCount()).isEqualTo(3);
        assertThat(response.participants()).extracting("userUuid").containsExactly(hostUuid.toString(),
            otherJoinerUuid.toString(), joinerUuid.toString());
        assertThat(response.participants().get(2).joinOrder()).isEqualTo(2);

        ArgumentCaptor<RelayRoomState> expectedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(expectedStateCaptor.capture(),
            updatedStateCaptor.capture());
        assertThat(expectedStateCaptor.getAllValues()).containsExactly(firstReadRoomState, secondReadRoomState);
        assertThat(updatedStateCaptor.getAllValues().get(0).participantCount()).isEqualTo(2);
        assertThat(updatedStateCaptor.getAllValues().get(1).participantCount()).isEqualTo(3);
    }

    /**
     * 짧은 재시도 횟수를 모두 소진하면 내부 오류로 올려 클라이언트에는 공통 500 응답이 나가게 합니다.
     */
    @Test
    void joinRoomFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        UUID joinerUuid = UUID.randomUUID();
        AppUser joiner = appUserWithNickname(joinerUuid, "포도");
        RelayRoomState roomState = roomState(participant(hostUuid, "망고", true, 0));
        given(anonymousUserResolver.resolve(joinerUuid.toString())).willReturn(joiner);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomService.joinRoom(joinerUuid.toString(), ROOM_CODE))
            .isInstanceOf(IllegalStateException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
    }

    private AppUser appUserWithNickname(UUID userUuid, String nickname) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt.plusHours(1));

        return appUser;
    }

    private RelayRoomState roomState(RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.WAITING, participants[0].userUuid(), 60, 2, 6, null,
            List.of(participants), createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }
}
