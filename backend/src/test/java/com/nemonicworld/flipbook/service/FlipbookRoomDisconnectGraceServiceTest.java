package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.disconnect.FlipbookDisconnectGraceProcessResult;
import com.nemonicworld.flipbook.service.disconnect.FlipbookDisconnectGraceRoomResult;
import com.nemonicworld.flipbook.service.disconnect.FlipbookHostChangeResult;
import com.nemonicworld.flipbook.service.disconnect.FlipbookRoomDisconnectGraceService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomDisconnectGraceServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String SECOND_ROOM_CODE = "GH4L8M";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 7, 15, 0, 10).truncatedTo(ChronoUnit.SECONDS);
    private static final long RECONNECT_GRACE_SECONDS = 10L;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomDisconnectGraceService flipbookRoomDisconnectGraceService;

    @BeforeEach
    void setUp() {
        flipbookRoomDisconnectGraceService = new FlipbookRoomDisconnectGraceService(flipbookRoomRepository,
            flipbookRoomEventPublisher, flipbookInviteMetadataSyncService, RECONNECT_GRACE_SECONDS, 100);
    }

    @Test
    void processRoomDoesNothingBeforeReconnectGraceExpires() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(9)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void processRoomDropsExpiredHostAndTransfersHost() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(10)),
            participant(participantUuid, "Peach", false, 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.droppedParticipants()).hasSize(1);
        assertThat(result.hostChange()).isNotNull();

        FlipbookRoomState updatedRoomState = captureUpdatedRoomState();
        FlipbookRoomParticipant droppedHost = updatedRoomState.participants().get(0);
        FlipbookRoomParticipant newHost = updatedRoomState.participants().get(1);
        assertThat(droppedHost.dropped()).isTrue();
        assertThat(droppedHost.droppedAt()).isEqualTo(NOW);
        assertThat(droppedHost.disconnectedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(droppedHost.host()).isFalse();
        assertThat(newHost.host()).isTrue();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(participantUuid.toString());

        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
        verify(flipbookRoomEventPublisher).publishParticipantDropped(result.droppedParticipants().get(0));
        verify(flipbookRoomEventPublisher).publishHostChanged(result.hostChange());
    }

    @Test
    void processRoomKeepsHostWhenNoConnectedCandidateExists() {
        UUID hostUuid = UUID.randomUUID();
        UUID disconnectedUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)),
            participant(disconnectedUuid, "Peach", false, 1, false, NOW.minusSeconds(3)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        FlipbookRoomState updatedRoomState = captureUpdatedRoomState();
        assertThat(result.hostChange()).isNull();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(hostUuid.toString());
        assertThat(updatedRoomState.participants().get(0).host()).isTrue();
        verify(flipbookRoomEventPublisher, never()).publishHostChanged(any(FlipbookHostChangeResult.class));
    }

    @Test
    void processRoomTransfersExistingDroppedHostWhenConnectedCandidateExists() {
        UUID droppedHostUuid = UUID.randomUUID();
        UUID candidateUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(
            droppedParticipant(droppedHostUuid, "Mango", true, 0, NOW.minusSeconds(20), NOW.minusSeconds(10)),
            participant(candidateUuid, "Peach", false, 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        FlipbookRoomState updatedRoomState = captureUpdatedRoomState();
        assertThat(result.droppedParticipants()).isEmpty();
        assertThat(result.hostChange()).isNotNull();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(candidateUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(FlipbookRoomParticipant::host).containsExactly(false,
            true);
        verify(flipbookRoomEventPublisher, never()).publishParticipantDropped(any());
        verify(flipbookRoomEventPublisher).publishHostChanged(result.hostChange());
    }

    @ParameterizedTest
    @EnumSource(value = FlipbookRoomStatus.class, names = {"WAITING", "FINALIZING", "FINISHED", "CLOSED"})
    void processRoomIgnoresNonPlayingRooms(FlipbookRoomStatus status) {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(status,
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void processRoomRetriesCasConflictAndPublishesEventsOnce() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false, true);

        FlipbookDisconnectGraceRoomResult result = flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        verify(flipbookRoomRepository, times(2)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
        verify(flipbookRoomEventPublisher, times(1)).publishParticipantDropped(any());
    }

    @Test
    void processRoomDoesNotPublishEventsWhenCasConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> flipbookRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW))
            .isInstanceOf(ConflictException.class).hasMessage("동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요.");

        verify(flipbookRoomRepository, times(3)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void processDroppedParticipantsContinuesAfterRoomFailure() {
        UUID firstHostUuid = UUID.randomUUID();
        UUID secondHostUuid = UUID.randomUUID();
        FlipbookRoomState firstRoom = playingRoom(ROOM_CODE,
            participant(firstHostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        FlipbookRoomState secondRoom = playingRoom(SECOND_ROOM_CODE,
            participant(secondHostUuid, "Peach", true, 0, false, NOW.minusSeconds(11)));
        given(flipbookRoomRepository.findPlayingRoomsForDisconnectGrace(eq(NOW.minusSeconds(10)), eq(100)))
            .willReturn(List.of(firstRoom, secondRoom));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willThrow(new IllegalStateException("boom"));
        given(flipbookRoomRepository.findByRoomCode(SECOND_ROOM_CODE)).willReturn(Optional.of(secondRoom));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookDisconnectGraceProcessResult result = flipbookRoomDisconnectGraceService
            .processDroppedParticipants(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(2);
        assertThat(result.processedRoomCount()).isEqualTo(1);
        assertThat(result.droppedParticipantCount()).isEqualTo(1);
    }

    private FlipbookRoomState captureUpdatedRoomState() {
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());

        return updatedStateCaptor.getValue();
    }

    private FlipbookRoomState playingRoom(FlipbookRoomParticipant... participants) {
        return playingRoom(ROOM_CODE, participants);
    }

    private FlipbookRoomState playingRoom(String roomCode, FlipbookRoomParticipant... participants) {
        return room(roomCode, FlipbookRoomStatus.PLAYING, participants);
    }

    private FlipbookRoomState room(FlipbookRoomStatus status, FlipbookRoomParticipant... participants) {
        return room(ROOM_CODE, status, participants);
    }

    private FlipbookRoomState room(String roomCode, FlipbookRoomStatus status,
        FlipbookRoomParticipant... participants) {
        LocalDateTime createdAt = NOW.minusMinutes(10);
        LocalDateTime startedAt = NOW.minusSeconds(30);

        if (status != FlipbookRoomStatus.PLAYING) {
            return new FlipbookRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, List.of(participants),
                createdAt, startedAt);
        }

        return new FlipbookRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, 1, 4, startedAt,
            startedAt.plusSeconds(45), startedAt, List.of(participants), createdAt, startedAt.plusSeconds(1),
            List.of());
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return participant(userUuid, nickname, host, joinOrder, true, null);
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            NOW.minusMinutes(5));
    }

    private FlipbookRoomParticipant droppedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
            NOW.minusMinutes(5), true, droppedAt);
    }
}
