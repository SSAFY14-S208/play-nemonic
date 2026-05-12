package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.nemonicworld.support.FlipbookRuntimeSettingsTestSupport.defaultFlipbookRoomPolicy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCloseResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseCommand;
import com.nemonicworld.flipbook.service.close.FlipbookRoomManualCloseUseCase;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomManualCloseUseCaseTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 11, 10, 30).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomManualCloseUseCase flipbookRoomManualCloseUseCase;

    @BeforeEach
    void setUp() {
        FlipbookRoomPolicy flipbookRoomPolicy = defaultFlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository);
        flipbookRoomManualCloseUseCase = new FlipbookRoomManualCloseUseCase(anonymousUserResolver, flipbookRoomPolicy,
            new FlipbookRoomCloseCommand(flipbookRoomRepository, flipbookInviteMetadataSyncService));
    }

    @Test
    void closeFinishedRoomByHostChangesStatusToClosed() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(FlipbookRoomStatus.FINISHED, participant(hostUuid, "망고", true, 0));
        givenValidUser(hostUuid);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomCloseResponse response = flipbookRoomManualCloseUseCase.closeRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(response.alreadyClosed()).isFalse();

        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(roomState), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    @Test
    void closeAlreadyClosedRoomReturnsIdempotentResponseWithoutSave() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(FlipbookRoomStatus.CLOSED, participant(hostUuid, "망고", true, 0));
        givenValidUser(hostUuid);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomCloseResponse response = flipbookRoomManualCloseUseCase.closeRoom(hostUuid.toString(), ROOM_CODE);

        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(response.closedAt()).isEqualTo(roomState.updatedAt());
        assertThat(response.alreadyClosed()).isTrue();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookInviteMetadataSyncService);
    }

    @Test
    void closeRoomRejectsNonHostParticipant() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(FlipbookRoomStatus.FINISHED, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "포도", false, 1));
        givenValidUser(participantUuid);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomManualCloseUseCase.closeRoom(participantUuid.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("방장만 사용할 수 있는 기능입니다.");

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookInviteMetadataSyncService);
    }

    @ParameterizedTest
    @CsvSource({"WAITING,결과 생성 전에는 방을 종료할 수 없습니다.", "PLAYING,게임 진행 중에는 방을 종료할 수 없습니다.",
        "FINALIZING,결과 생성 중에는 방을 종료할 수 없습니다."})
    void closeRoomRejectsNonFinishedRooms(FlipbookRoomStatus roomStatus, String expectedMessage) {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(roomStatus, participant(hostUuid, "망고", true, 0));
        givenValidUser(hostUuid);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        assertThatThrownBy(() -> flipbookRoomManualCloseUseCase.closeRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage(expectedMessage);

        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookInviteMetadataSyncService);
    }

    @Test
    void closeRoomRetriesRedisSaveConflict() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(FlipbookRoomStatus.FINISHED, participant(hostUuid, "망고", true, 0));
        givenValidUser(hostUuid);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> flipbookRoomManualCloseUseCase.closeRoom(hostUuid.toString(), ROOM_CODE))
            .isInstanceOf(ConflictException.class).hasMessage(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);

        verify(flipbookRoomRepository, times(FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES))
            .saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class));
        verifyNoInteractions(flipbookInviteMetadataSyncService);
    }

    private void givenValidUser(UUID userUuid) {
        given(anonymousUserResolver.resolve(userUuid.toString()))
            .willReturn(AppUser.createAnonymous(userUuid, "MockAgent/1.0", NOW.minusDays(1)));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
    }

    private FlipbookRoomState room(FlipbookRoomStatus status, FlipbookRoomParticipant... participants) {
        String hostUserUuid = List.of(participants).stream().filter(FlipbookRoomParticipant::host).findFirst()
            .map(FlipbookRoomParticipant::userUuid).orElse(participants[0].userUuid());

        return new FlipbookRoomState(ROOM_CODE, status, hostUserUuid, 45, 2, 6, 8, 8, NOW.minusMinutes(10),
            NOW.minusMinutes(9), NOW.minusMinutes(30), List.of(), List.of(participants), NOW.minusMinutes(40),
            NOW.minusMinutes(1), List.of());
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            NOW.minusMinutes(40).plusSeconds(joinOrder));
    }
}
