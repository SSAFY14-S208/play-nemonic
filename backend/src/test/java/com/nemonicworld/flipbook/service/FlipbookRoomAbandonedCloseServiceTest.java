package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.close.FlipbookRoomAbandonedCloseProcessResult;
import com.nemonicworld.flipbook.service.close.FlipbookRoomAbandonedCloseService;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseCommand;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomAbandonedCloseServiceTest {

    private static final String WAITING_ROOM_CODE = "FWAIT1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 11, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    private FlipbookRoomAbandonedCloseService service;

    @BeforeEach
    void setUp() {
        service = new FlipbookRoomAbandonedCloseService(flipbookRoomRepository,
            new FlipbookRoomCloseCommand(flipbookRoomRepository, flipbookInviteMetadataSyncService),
            flipbookRoomEventPublisher, 300, 10);
    }

    @Test
    void closeAbandonedRoomsClosesDisconnectedWaitingRooms() {
        FlipbookRoomState waitingRoom = room(WAITING_ROOM_CODE, FlipbookRoomStatus.WAITING, NOW.minusMinutes(6),
            disconnectedParticipant(UUID.randomUUID(), true, 0, NOW.minusMinutes(6)));
        given(flipbookRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(flipbookRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10))
            .willReturn(List.of(waitingRoom));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.scannedWaitingRoomCount()).isEqualTo(1);
        assertThat(result.closedWaitingRoomCount()).isEqualTo(1);
        verify(flipbookRoomEventPublisher).publishRoomClosed(WAITING_ROOM_CODE, NOW);
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(waitingRoom), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    @Test
    void closeAbandonedRoomsClosesEmptyWaitingRooms() {
        FlipbookRoomState emptyWaitingRoom = emptyWaitingRoom();
        given(flipbookRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of(emptyWaitingRoom));
        given(flipbookRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.scannedWaitingRoomCount()).isEqualTo(1);
        assertThat(result.closedWaitingRoomCount()).isEqualTo(1);
        verify(flipbookRoomEventPublisher).publishRoomClosed(WAITING_ROOM_CODE, NOW);
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(emptyWaitingRoom), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(updatedStateCaptor.getValue().participants()).isEmpty();
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    @Test
    void closeAbandonedRoomsDoesNotPublishWhenCasConflictOccurs() {
        FlipbookRoomState waitingRoom = room(WAITING_ROOM_CODE, FlipbookRoomStatus.WAITING, NOW.minusMinutes(6),
            disconnectedParticipant(UUID.randomUUID(), true, 0, NOW.minusMinutes(6)));
        given(flipbookRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(flipbookRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10))
            .willReturn(List.of(waitingRoom));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false);

        FlipbookRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.closedWaitingRoomCount()).isZero();
        verify(flipbookRoomEventPublisher, never()).publishRoomClosed(any(), any());
        verify(flipbookInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    @Test
    void closeAbandonedRoomsUsesConfiguredCutoff() {
        given(flipbookRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(flipbookRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());

        service.closeAbandonedRooms(NOW);

        verify(flipbookRoomRepository).findEmptyWaitingRooms(10);
        verify(flipbookRoomRepository).findAbandonedWaitingRooms(NOW.minusMinutes(5), 10);
    }

    private FlipbookRoomState room(String roomCode, FlipbookRoomStatus status, LocalDateTime updatedAt,
        FlipbookRoomParticipant... participants) {
        LocalDateTime createdAt = updatedAt.minusMinutes(10);

        return new FlipbookRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, List.of(participants),
            createdAt, updatedAt, List.of());
    }

    private FlipbookRoomState emptyWaitingRoom() {
        return new FlipbookRoomState(WAITING_ROOM_CODE, FlipbookRoomStatus.WAITING, null, 45, 2, 6, List.of(),
            NOW.minusMinutes(10), NOW.minusMinutes(6), List.of());
    }

    private FlipbookRoomParticipant disconnectedParticipant(UUID userUuid, boolean host, int joinOrder,
        LocalDateTime disconnectedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, false,
            disconnectedAt, NOW.minusMinutes(10));
    }
}
