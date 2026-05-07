package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.response.RelayRoomResultsResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayResultArtifactRow;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.result.RelayRoomResultQueryUseCase;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayRoomResultQueryUseCaseTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final UUID VIEWER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 16, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RelayArtifactRepository relayArtifactRepository;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    private RelayRoomResultQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        RelayRoomPolicy relayRoomPolicy = new RelayRoomPolicy(roomCodeGenerator, relayRoomRepository);
        useCase = new RelayRoomResultQueryUseCase(anonymousUserResolver, relayArtifactRepository, relayRoomRepository,
            relayRoomPolicy, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void getResultsReturnsOwnedResultsSortedByCanvasIndexEvenWhenRedisExpired() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayArtifactRepository.findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of(resultRow(1), resultRow(0)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());

        RelayRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(response.roomStatus()).isNull();
        assertThat(response.ready()).isTrue();
        assertThat(response.resultCount()).isEqualTo(2);
        assertThat(response.results()).extracting("canvasIndex").containsExactly(0, 1);
        assertThat(response.results().get(0).contentUrl()).contains("/original.png");
        assertThat(response.results().get(0).thumbnailUrl()).contains("/thumbnail.png");
        assertThat(response.results().get(0).parts()).extracting("part").containsExactly(RelayDrawingPart.FACE,
            RelayDrawingPart.BODY, RelayDrawingPart.LEGS);
        assertThat(response.results().get(0).parts()).extracting("drawerNickname").containsExactly("Mango", "Peach",
            "Berry");
        verify(relayArtifactRepository, never()).countRelayResultsByRoomCode(ROOM_CODE);
    }

    @Test
    void getResultsReturnsReadyFalseForParticipantBeforeResultsAreCreated() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayArtifactRepository.findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState()));
        given(relayArtifactRepository.countRelayResultsByRoomCode(ROOM_CODE)).willReturn(0L);

        RelayRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.ready()).isFalse();
        assertThat(response.roomStatus()).isEqualTo(RelayRoomStatus.FINALIZING);
        assertThat(response.resultCount()).isZero();
        assertThat(response.results()).isEmpty();
    }

    @Test
    void getResultsRejectsUserWithoutActiveGalleryWhenRoomResultsExist() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayArtifactRepository.findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());
        given(relayArtifactRepository.countRelayResultsByRoomCode(ROOM_CODE)).willReturn(3L);

        assertThatThrownBy(() -> useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("릴레이 결과를 조회할 권한이 없습니다.");
    }

    @Test
    void getResultsReturnsNotFoundWhenNoRedisAndNoDbResultExist() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayArtifactRepository.findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());
        given(relayArtifactRepository.countRelayResultsByRoomCode(ROOM_CODE)).willReturn(0L);

        assertThatThrownBy(() -> useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE))
            .isInstanceOf(NotFoundException.class).hasMessage("릴레이 결과를 찾을 수 없습니다.");
    }

    @Test
    void getResultsDoesNotFailWhenMetaIsMalformed() {
        UUID artifactId = UUID.randomUUID();
        RelayResultArtifactRow malformedRow = new RelayResultArtifactRow(UUID.randomUUID(), artifactId,
            "relay/results/%s/thumbnail.png".formatted(artifactId),
            "relay/results/%s/original.png".formatted(artifactId), "not-json", NOW);
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(relayArtifactRepository.findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of(malformedRow));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());

        RelayRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.ready()).isTrue();
        assertThat(response.results().get(0).canvasIndex()).isNull();
        assertThat(response.results().get(0).parts()).isEmpty();
    }

    private AppUser appUser(UUID userUuid) {
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", NOW.minusDays(1));
        appUser.updateNickname("Mango", NOW.minusHours(1));

        return appUser;
    }

    private RelayResultArtifactRow resultRow(int canvasIndex) {
        UUID artifactId = UUID.randomUUID();
        return new RelayResultArtifactRow(UUID.randomUUID(), artifactId,
            "relay/results/%s/thumbnail.png".formatted(artifactId),
            "relay/results/%s/original.png".formatted(artifactId), meta(canvasIndex), NOW.plusSeconds(canvasIndex));
    }

    private String meta(int canvasIndex) {
        return """
            {
              "canvasIndex": %d,
              "roomCode": "%s",
              "parts": [
                {"part": "FACE", "drawerUserUuid": "%s", "drawerNickname": "Mango"},
                {"part": "BODY", "drawerUserUuid": "%s", "drawerNickname": "Peach"},
                {"part": "LEGS", "drawerUserUuid": "%s", "drawerNickname": "Berry"}
              ]
            }
            """.formatted(canvasIndex, ROOM_CODE, VIEWER_UUID, UUID.randomUUID(), UUID.randomUUID());
    }

    private RelayRoomState roomState() {
        RelayRoomParticipant participant = new RelayRoomParticipant(VIEWER_UUID.toString(), "Mango", true, 0, true,
            null, NOW.minusMinutes(5));

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.FINALIZING, VIEWER_UUID.toString(), 45, 2, 6,
            RelayDrawingPart.LEGS, List.of(participant), List.of(), NOW.minusMinutes(3), NOW.minusMinutes(2),
            NOW.minusMinutes(10), NOW.minusMinutes(20), NOW);
    }
}
