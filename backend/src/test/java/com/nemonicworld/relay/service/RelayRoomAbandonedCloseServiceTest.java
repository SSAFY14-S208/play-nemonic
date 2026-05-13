package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.close.RelayRoomAbandonedCloseProcessResult;
import com.nemonicworld.relay.service.close.RelayRoomAbandonedCloseService;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class RelayRoomAbandonedCloseServiceTest {

    private static final String WAITING_ROOM_CODE = "WAIT01";
    private static final String PLAYING_ROOM_CODE = "PLAY01";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 15, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayRoomEventPublisher relayRoomEventPublisher;

    @Mock
    private RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    private RelayRoomAbandonedCloseService service;

    @BeforeEach
    void setUp() {
        service = new RelayRoomAbandonedCloseService(relayRoomRepository,
            new RelayRoomCloseCommand(relayRoomRepository, relayInviteMetadataSyncService), relayRoomEventPublisher,
            300, 300, 10);
    }

    @Test
    void closeAbandonedRoomsClosesWaitingAndPlayingRooms() {
        RelayRoomState waitingRoom = room(WAITING_ROOM_CODE, RelayRoomStatus.WAITING, NOW.minusMinutes(6),
            disconnectedParticipant(UUID.randomUUID(), true, 0, NOW.minusMinutes(6)));
        RelayRoomState playingRoom = room(PLAYING_ROOM_CODE, RelayRoomStatus.PLAYING, NOW.minusMinutes(6),
            disconnectedParticipant(UUID.randomUUID(), true, 0, NOW.minusMinutes(6)),
            droppedParticipant(UUID.randomUUID(), false, 1, NOW.minusMinutes(6), NOW.minusMinutes(6)));
        given(relayRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of(waitingRoom));
        given(relayRoomRepository.findAbandonedPlayingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of(playingRoom));
        given(relayRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.scannedWaitingRoomCount()).isEqualTo(1);
        assertThat(result.closedWaitingRoomCount()).isEqualTo(1);
        assertThat(result.scannedPlayingRoomCount()).isEqualTo(1);
        assertThat(result.closedPlayingRoomCount()).isEqualTo(1);
        verify(relayRoomEventPublisher).publishRoomClosed(WAITING_ROOM_CODE, NOW, "waiting_idle_timeout");
        verify(relayRoomEventPublisher).publishRoomClosed(PLAYING_ROOM_CODE, NOW, "playing_abandoned");
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository, times(2)).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getAllValues()).extracting(RelayRoomState::status)
            .containsOnly(RelayRoomStatus.CLOSED);
        verify(relayInviteMetadataSyncService, times(2)).syncWithRoomState(any(RelayRoomState.class));
    }

    @Test
    void closeAbandonedRoomsDoesNotPublishWhenCasConflictOccurs() {
        RelayRoomState waitingRoom = room(WAITING_ROOM_CODE, RelayRoomStatus.WAITING, NOW.minusMinutes(6),
            disconnectedParticipant(UUID.randomUUID(), true, 0, NOW.minusMinutes(6)));
        given(relayRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of(waitingRoom));
        given(relayRoomRepository.findAbandonedPlayingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());
        given(relayRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        RelayRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.closedWaitingRoomCount()).isZero();
        verify(relayRoomEventPublisher, never()).publishRoomClosed(any(), any(), any());
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    @Test
    void closeAbandonedRoomsUsesConfiguredCutoffs() {
        given(relayRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of());
        given(relayRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());
        given(relayRoomRepository.findAbandonedPlayingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());

        service.closeAbandonedRooms(NOW);

        verify(relayRoomRepository).findEmptyWaitingRooms(10);
        verify(relayRoomRepository).findAbandonedWaitingRooms(NOW.minusMinutes(5), 10);
        verify(relayRoomRepository).findAbandonedPlayingRooms(NOW.minusMinutes(5), 10);
    }

    @Test
    void closeAbandonedRoomsClosesEmptyWaitingRooms() {
        RelayRoomState emptyWaitingRoom = emptyWaitingRoom();
        given(relayRoomRepository.findEmptyWaitingRooms(10)).willReturn(List.of(emptyWaitingRoom));
        given(relayRoomRepository.findAbandonedWaitingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());
        given(relayRoomRepository.findAbandonedPlayingRooms(NOW.minusMinutes(5), 10)).willReturn(List.of());
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomAbandonedCloseProcessResult result = service.closeAbandonedRooms(NOW);

        assertThat(result.scannedWaitingRoomCount()).isEqualTo(1);
        assertThat(result.closedWaitingRoomCount()).isEqualTo(1);
        verify(relayRoomEventPublisher).publishRoomClosed(WAITING_ROOM_CODE, NOW, "waiting_empty");
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(eq(emptyWaitingRoom), updatedStateCaptor.capture());
        assertThat(updatedStateCaptor.getValue().status()).isEqualTo(RelayRoomStatus.CLOSED);
        assertThat(updatedStateCaptor.getValue().participants()).isEmpty();
        verify(relayInviteMetadataSyncService).syncWithRoomState(updatedStateCaptor.getValue());
    }

    private RelayRoomState room(String roomCode, RelayRoomStatus status, LocalDateTime updatedAt,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = updatedAt.minusMinutes(10);

        return new RelayRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, RelayDrawingPart.FACE,
            List.of(participants), List.of(), NOW.minusMinutes(7), NOW.minusMinutes(6), NOW.minusMinutes(10), createdAt,
            updatedAt);
    }

    private RelayRoomState emptyWaitingRoom() {
        return new RelayRoomState(WAITING_ROOM_CODE, RelayRoomStatus.WAITING, null, 45, 2, 6, null, List.of(),
            List.of(), null, null, null, NOW.minusMinutes(10), NOW.minusMinutes(6));
    }

    private RelayRoomParticipant disconnectedParticipant(UUID userUuid, boolean host, int joinOrder,
        LocalDateTime disconnectedAt) {
        return new RelayRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, false,
            disconnectedAt, NOW.minusMinutes(10));
    }

    private RelayRoomParticipant droppedParticipant(UUID userUuid, boolean host, int joinOrder,
        LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
        return new RelayRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, false,
            disconnectedAt, NOW.minusMinutes(10), true, droppedAt);
    }
}
