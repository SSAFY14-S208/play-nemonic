package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.connection.RelayRoomConnectionReconciliationResult;
import com.nemonicworld.relay.service.connection.RelayRoomConnectionReconciliationService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
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
class RelayRoomConnectionReconciliationServiceTest {

    private static final String ROOM_CODE = "WAIT01";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 11, 22, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @Mock
    private RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    private RelayRoomConnectionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new RelayRoomConnectionReconciliationService(relayRoomRepository, webSocketSessionRegistry,
            relayInviteMetadataSyncService, 10);
    }

    @Test
    void reconcileConnectionsMarksWaitingParticipantDisconnectedWhenRelaySessionIsMissing() {
        UUID userUuid = UUID.randomUUID();
        RelayRoomState roomState = room(RelayRoomStatus.WAITING, participant(userUuid, true, 0));
        given(relayRoomRepository.findRoomsForConnectionReconciliation(10)).willReturn(List.of(roomState));
        given(webSocketSessionRegistry.hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE,
            userUuid.toString())).willReturn(false);
        given(relayRoomRepository.saveIfUnchanged(eq(roomState), any(RelayRoomState.class))).willReturn(true);

        RelayRoomConnectionReconciliationResult result = service.reconcileConnections(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(1);
        assertThat(result.reconciledRoomCount()).isEqualTo(1);
        assertThat(result.reconciledParticipantCount()).isEqualTo(1);
        ArgumentCaptor<RelayRoomState> updatedRoomCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(eq(roomState), updatedRoomCaptor.capture());
        RelayRoomParticipant updatedParticipant = updatedRoomCaptor.getValue().participants().get(0);
        assertThat(updatedParticipant.connected()).isFalse();
        assertThat(updatedParticipant.disconnectedAt()).isEqualTo(NOW);
        verify(relayInviteMetadataSyncService).syncWithRoomState(updatedRoomCaptor.getValue());
    }

    @Test
    void reconcileConnectionsMarksPlayingParticipantDisconnectedWhenRelaySessionIsMissing() {
        UUID userUuid = UUID.randomUUID();
        RelayRoomState roomState = room(RelayRoomStatus.PLAYING, participant(userUuid, true, 0));
        given(relayRoomRepository.findRoomsForConnectionReconciliation(10)).willReturn(List.of(roomState));
        given(webSocketSessionRegistry.hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE,
            userUuid.toString())).willReturn(false);
        given(relayRoomRepository.saveIfUnchanged(eq(roomState), any(RelayRoomState.class))).willReturn(true);

        service.reconcileConnections(NOW);

        ArgumentCaptor<RelayRoomState> updatedRoomCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(eq(roomState), updatedRoomCaptor.capture());
        assertThat(updatedRoomCaptor.getValue().status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(updatedRoomCaptor.getValue().participants().get(0).connected()).isFalse();
    }

    @Test
    void reconcileConnectionsDoesNothingWhenRelaySessionExists() {
        UUID userUuid = UUID.randomUUID();
        RelayRoomState roomState = room(RelayRoomStatus.WAITING, participant(userUuid, true, 0));
        given(relayRoomRepository.findRoomsForConnectionReconciliation(10)).willReturn(List.of(roomState));
        given(webSocketSessionRegistry.hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE,
            userUuid.toString())).willReturn(true);

        RelayRoomConnectionReconciliationResult result = service.reconcileConnections(NOW);

        assertThat(result.reconciledRoomCount()).isZero();
        assertThat(result.reconciledParticipantCount()).isZero();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    @Test
    void reconcileConnectionsDoesNotSyncWhenCasConflictOccurs() {
        UUID userUuid = UUID.randomUUID();
        RelayRoomState roomState = room(RelayRoomStatus.WAITING, participant(userUuid, true, 0));
        given(relayRoomRepository.findRoomsForConnectionReconciliation(10)).willReturn(List.of(roomState));
        given(webSocketSessionRegistry.hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE,
            userUuid.toString())).willReturn(false);
        given(relayRoomRepository.saveIfUnchanged(eq(roomState), any(RelayRoomState.class))).willReturn(false);

        RelayRoomConnectionReconciliationResult result = service.reconcileConnections(NOW);

        assertThat(result.reconciledRoomCount()).isZero();
        verify(relayInviteMetadataSyncService, never()).syncWithRoomState(any());
    }

    @Test
    void reconcileConnectionsChecksOnlyRelayConnectionType() {
        UUID userUuid = UUID.randomUUID();
        RelayRoomState roomState = room(RelayRoomStatus.WAITING, participant(userUuid, true, 0));
        given(relayRoomRepository.findRoomsForConnectionReconciliation(10)).willReturn(List.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(eq(roomState), any(RelayRoomState.class))).willReturn(true);

        service.reconcileConnections(NOW);

        verify(webSocketSessionRegistry).hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE,
            userUuid.toString());
    }

    private RelayRoomState room(RelayRoomStatus status, RelayRoomParticipant... participants) {
        return new RelayRoomState(ROOM_CODE, status, participants[0].userUuid(), 45, 2, 6, RelayDrawingPart.FACE,
            List.of(participants), List.of(), NOW.minusMinutes(1), NOW.plusSeconds(45), NOW.minusMinutes(5),
            NOW.minusMinutes(10), NOW.minusMinutes(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, true,
            null, NOW.minusMinutes(10));
    }
}
