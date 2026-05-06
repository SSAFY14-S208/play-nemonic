package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.close.RelayRoomCloseProcessResult;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import com.nemonicworld.relay.service.close.RelayRoomCloseService;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class RelayRoomCloseServiceTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String SECOND_ROOM_CODE = "CD4L8M";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 15, 5).truncatedTo(ChronoUnit.SECONDS);
    private static final long CLOSE_DELAY_SECONDS = 300L;
    private static final int SCAN_LIMIT = 10;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayRoomEventPublisher relayRoomEventPublisher;

    private RelayRoomCloseService relayRoomCloseService;

    @BeforeEach
    void setUp() {
        relayRoomCloseService = new RelayRoomCloseService(relayRoomRepository, relayRoomEventPublisher,
            CLOSE_DELAY_SECONDS, SCAN_LIMIT);
    }

    @Test
    void closeRoomDoesNothingBeforeCloseDelay() {
        RelayRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusSeconds(CLOSE_DELAY_SECONDS - 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomCloseResult result = relayRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomClosesFinishedRoomAfterCloseDelay() {
        RelayRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusSeconds(CLOSE_DELAY_SECONDS));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomCloseResult result = relayRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isTrue();
        assertThat(result.closedAt()).isEqualTo(NOW);
        assertThat(result.roomState().status()).isEqualTo(RelayRoomStatus.CLOSED);
        assertThat(result.roomState().updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(eq(roomState), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(RelayRoomStatus.CLOSED);
        verify(relayRoomEventPublisher).publishRoomClosed(ROOM_CODE, NOW);
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"WAITING", "PLAYING", "FINALIZING", "CLOSED"})
    void closeRoomIgnoresNonFinishedRooms(RelayRoomStatus roomStatus) {
        RelayRoomState roomState = room(ROOM_CODE, roomStatus, NOW.minusMinutes(10));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomCloseResult result = relayRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomDoesNotPublishEventWhenRedisSaveKeepsConflicting() {
        RelayRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomCloseService.closeRoom(ROOM_CODE, NOW))
            .isInstanceOf(IllegalStateException.class);

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomRetriesRedisSaveConflictAndPublishesEventOnce() {
        RelayRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomCloseResult result = relayRoomCloseService.closeRoom(ROOM_CODE, NOW);

        assertThat(result.closed()).isTrue();
        verify(relayRoomRepository, times(2)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verify(relayRoomEventPublisher, times(1)).publishRoomClosed(ROOM_CODE, NOW);
    }

    @Test
    void closeRoomDoesNotDuplicateEventAfterAlreadyClosed() {
        RelayRoomState roomState = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);
        RelayRoomCloseResult firstResult = relayRoomCloseService.closeRoom(ROOM_CODE, NOW);
        clearInvocations(relayRoomRepository, relayRoomEventPublisher);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstResult.roomState()));

        RelayRoomCloseResult secondResult = relayRoomCloseService.closeRoom(ROOM_CODE, NOW.plusSeconds(1));

        assertThat(secondResult.closed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeFinishedRoomsScansClosableRoomsAndClosesOnlySuccessfulRooms() {
        RelayRoomState firstRoom = finishedRoom(ROOM_CODE, NOW.minusMinutes(10));
        RelayRoomState secondRoom = finishedRoom(SECOND_ROOM_CODE, NOW.minusMinutes(10));
        given(relayRoomRepository.findClosableFinishedRooms(NOW.minusSeconds(CLOSE_DELAY_SECONDS), SCAN_LIMIT))
            .willReturn(List.of(firstRoom, secondRoom));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willThrow(new IllegalStateException("boom"));
        given(relayRoomRepository.findByRoomCode(SECOND_ROOM_CODE)).willReturn(Optional.of(secondRoom));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomCloseProcessResult result = relayRoomCloseService.closeFinishedRooms(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(2);
        assertThat(result.closedRoomCount()).isEqualTo(1);
        verify(relayRoomEventPublisher).publishRoomClosed(SECOND_ROOM_CODE, NOW);
        verify(relayRoomEventPublisher, never()).publishRoomClosed(eq(ROOM_CODE), any(LocalDateTime.class));
    }

    private RelayRoomState finishedRoom(String roomCode, LocalDateTime updatedAt) {
        return room(roomCode, RelayRoomStatus.FINISHED, updatedAt);
    }

    private RelayRoomState room(String roomCode, RelayRoomStatus status, LocalDateTime updatedAt) {
        LocalDateTime createdAt = NOW.minusMinutes(20);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = new RelayRoomParticipant(hostUuid.toString(), "Mango", true, 0, true, null,
            createdAt);

        return new RelayRoomState(roomCode, status, hostUuid.toString(), 45, 2, 6, RelayDrawingPart.LEGS, List.of(host),
            List.of(), NOW.minusMinutes(11), NOW.minusMinutes(10), NOW.minusMinutes(15), createdAt, updatedAt);
    }
}
