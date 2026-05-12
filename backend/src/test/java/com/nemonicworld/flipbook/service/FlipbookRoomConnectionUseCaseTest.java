package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.nemonicworld.support.FlipbookRuntimeSettingsTestSupport.defaultFlipbookRoomPolicy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 플립북 WebSocket 연결 상태 갱신 유스케이스를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FlipbookRoomConnectionUseCaseTest {

    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomConnectionUseCase flipbookRoomConnectionUseCase;

    @BeforeEach
    void setUp() {
        FlipbookRoomPolicy flipbookRoomPolicy = defaultFlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository);
        FlipbookRoomViewerFactory flipbookRoomViewerFactory = new FlipbookRoomViewerFactory(flipbookRoomPolicy);
        flipbookRoomConnectionUseCase = new FlipbookRoomConnectionUseCase(anonymousUserResolver, flipbookRoomRepository,
            flipbookRoomPolicy, flipbookRoomViewerFactory, flipbookInviteMetadataSyncService);
    }

    /**
     * 참여자가 WebSocket에 연결되면 Redis 참여자 상태를 connected=true로 갱신합니다.
     */
    @Test
    void connectRoomMarksParticipantConnected() {
        UUID userUuid = UUID.randomUUID();
        AppUser user = appUserWithNickname(userUuid, "망고");
        FlipbookRoomState roomState = roomState(participant(userUuid, "망고", true, false));
        given(anonymousUserResolver.resolve(userUuid.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomStateResponse response = flipbookRoomConnectionUseCase.connectRoom(userUuid.toString(), ROOM_CODE);

        assertThat(response.participants().get(0).connected()).isTrue();
        assertThat(response.viewer().participant()).isTrue();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().participants().get(0).connected()).isTrue();
        assertThat(updatedStateCaptor.getValue().participants().get(0).disconnectedAt()).isNull();
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    /**
     * 연결 해제 시에는 UUID 형식만 확인한 뒤 Redis 참여자 상태를 connected=false로 갱신합니다.
     */
    @Test
    void disconnectRoomMarksParticipantDisconnected() {
        UUID userUuid = UUID.randomUUID();
        FlipbookRoomState roomState = roomState(participant(userUuid, "망고", true, true));
        given(anonymousUserResolver.parseUuid(userUuid.toString())).willReturn(userUuid);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomStateResponse response = flipbookRoomConnectionUseCase.disconnectRoom(userUuid.toString(),
            ROOM_CODE);

        assertThat(response.participants().get(0).connected()).isFalse();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().participants().get(0).connected()).isFalse();
        assertThat(updatedStateCaptor.getValue().participants().get(0).disconnectedAt()).isNotNull();
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    /**
     * 연결 상태 저장 충돌이 반복되면 클라이언트가 재시도할 수 있는 409 예외를 던집니다.
     */
    @Test
    void connectRoomFailsWhenOptimisticSaveConflictsKeepHappening() {
        UUID userUuid = UUID.randomUUID();
        AppUser user = appUserWithNickname(userUuid, "망고");
        FlipbookRoomState roomState = roomState(participant(userUuid, "망고", true, false));
        given(anonymousUserResolver.resolve(userUuid.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> flipbookRoomConnectionUseCase.connectRoom(userUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("동시 접속 상태 변경 요청이 많아 플립북 방 연결 상태를 갱신하지 못했습니다. 다시 시도해주세요.");

        verify(flipbookRoomRepository, times(3)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
    }

    /**
     * 게임 중 끊긴 참여자는 10초 재접속 유예 시간이 지나면 WebSocket 재연결이 거부됩니다.
     */
    @Test
    void connectRoomRejectsReconnectAfterGracePeriod() {
        UUID userUuid = UUID.randomUUID();
        AppUser user = appUserWithNickname(userUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.PLAYING,
            disconnectedParticipant(userUuid, "망고", true, 11));
        given(anonymousUserResolver.resolve(userUuid.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomConnectionUseCase.connectRoom(userUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(flipbookInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    /**
     * 이미 이탈 확정된 참여자의 WebSocket 재연결은 거부됩니다.
     */
    @Test
    void connectRoomRejectsDroppedParticipant() {
        UUID userUuid = UUID.randomUUID();
        AppUser user = appUserWithNickname(userUuid, "망고");
        FlipbookRoomState roomState = roomState(FlipbookRoomStatus.PLAYING, droppedParticipant(userUuid, "망고", true));
        given(anonymousUserResolver.resolve(userUuid.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomConnectionUseCase.connectRoom(userUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage("재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(flipbookInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    /**
     * 강퇴된 UUID의 WebSocket 재연결은 participant 연결 갱신 전에 거부합니다.
     */
    @Test
    void connectRoomRejectsKickedUser() {
        UUID userUuid = UUID.randomUUID();
        AppUser user = appUserWithNickname(userUuid, "망고");
        FlipbookRoomState roomState = roomState(participant(userUuid, "망고", true, false))
            .withParticipantsAndKickedUserUuids(List.of(), List.of(userUuid.toString()), LocalDateTime.now());
        given(anonymousUserResolver.resolve(userUuid.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomConnectionUseCase.connectRoom(userUuid.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("강퇴된 방에는 다시 입장할 수 없습니다.");
    }

    private FlipbookRoomState roomState(FlipbookRoomParticipant participant) {
        return roomState(FlipbookRoomStatus.WAITING, participant);
    }

    private FlipbookRoomState roomState(FlipbookRoomStatus status, FlipbookRoomParticipant participant) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, status, participant.userUuid(), 45, 2, 6, List.of(participant), now,
            now);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, boolean connected) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, 0, connected,
            connected ? null : LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private FlipbookRoomParticipant disconnectedParticipant(UUID userUuid, String nickname, boolean host,
        int disconnectedSecondsAgo) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, 0, false,
            LocalDateTime.now().minusSeconds(disconnectedSecondsAgo).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private FlipbookRoomParticipant droppedParticipant(UUID userUuid, String nickname, boolean host) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, 0, false, now.minusSeconds(20),
            now.minusMinutes(1), true, now.minusSeconds(10));
    }

    private AppUser appUserWithNickname(UUID userUuid, String nickname) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt.plusHours(1));

        return appUser;
    }
}
