package com.nemonicworld.relay.service.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.relay.dto.response.RelayRoomResultItemResponse;
import com.nemonicworld.relay.dto.response.RelayRoomResultPartResponse;
import com.nemonicworld.relay.dto.response.RelayRoomResultsResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayResultArtifactRow;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 최종 결과 화면에서 사용할 gallery 소유 결과물을 조회합니다.
 */
@Service
public class RelayRoomResultQueryUseCase {

    private static final String RESULT_NOT_FOUND_MESSAGE = "릴레이 결과를 찾을 수 없습니다.";
    private static final String RESULT_ACCESS_DENIED_MESSAGE = "릴레이 결과를 조회할 권한이 없습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayArtifactRepository relayArtifactRepository;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final ObjectMapper objectMapper;

    public RelayRoomResultQueryUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayArtifactRepository relayArtifactRepository, RelayRoomRepository relayRoomRepository,
        RelayRoomPolicy relayRoomPolicy, ObjectMapper objectMapper) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.objectMapper = objectMapper;
    }

    /**
     * PostgreSQL의 artifact/gallery를 기준으로 요청 사용자가 소유한 최종 결과를 반환합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomResultsResponse getResults(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        UUID viewerUserUuid = viewerUser.getId();
        List<RelayResultArtifactRow> rows = relayArtifactRepository
            .findActiveRelayResultsByRoomCodeAndUserUuid(roomCodeValue, viewerUserUuid);
        Optional<RelayRoomState> roomState = relayRoomRepository.findByRoomCode(roomCodeValue);

        if (!rows.isEmpty()) {
            List<RelayRoomResultItemResponse> results = rows.stream().sorted(resultRowComparator())
                .map(this::toResponse).toList();

            return new RelayRoomResultsResponse(roomCodeValue, roomState.map(RelayRoomState::status).orElse(null), true,
                results.size(), results);
        }

        if (relayArtifactRepository.countRelayResultsByRoomCode(roomCodeValue) > 0) {
            throw new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE);
        }

        RelayRoomState existingRoomState = roomState.orElseThrow(() -> new NotFoundException(RESULT_NOT_FOUND_MESSAGE));
        validateResultPollingAllowed(existingRoomState, viewerUserUuid.toString());

        return new RelayRoomResultsResponse(roomCodeValue, existingRoomState.status(), false, 0, List.of());
    }

    private void validateResultPollingAllowed(RelayRoomState roomState, String viewerUserUuid) {
        RelayRoomParticipant participant = relayRoomPolicy.findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE));
        if (participant.dropped()) {
            throw new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE);
        }
    }

    private RelayRoomResultItemResponse toResponse(RelayResultArtifactRow row) {
        return new RelayRoomResultItemResponse(extractCanvasIndex(row.meta()), row.galleryId().toString(),
            row.artifactId().toString(), row.thumbnailUrl(), row.contentUrl(), row.createdAt(),
            extractParts(row.meta()));
    }

    private Comparator<RelayResultArtifactRow> resultRowComparator() {
        return Comparator
            .comparing((RelayResultArtifactRow row) -> extractCanvasIndex(row.meta()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RelayResultArtifactRow::createdAt, Comparator.nullsLast(LocalDateTime::compareTo))
            .thenComparing(row -> row.artifactId().toString());
    }

    private Integer extractCanvasIndex(String meta) {
        try {
            JsonNode metaNode = objectMapper.readTree(meta);
            JsonNode canvasIndexNode = metaNode.path("canvasIndex");
            return canvasIndexNode.canConvertToInt() ? canvasIndexNode.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<RelayRoomResultPartResponse> extractParts(String meta) {
        try {
            JsonNode partsNode = objectMapper.readTree(meta).path("parts");
            if (!partsNode.isArray()) {
                return List.of();
            }

            return java.util.stream.StreamSupport.stream(partsNode.spliterator(), false).map(this::toPartResponse)
                .flatMap(Optional::stream).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Optional<RelayRoomResultPartResponse> toPartResponse(JsonNode partNode) {
        if (partNode.isTextual()) {
            return parsePart(partNode.asText()).map(part -> new RelayRoomResultPartResponse(part, null, null));
        }

        RelayDrawingPart part = parsePart(partNode.path("part").asText(null)).orElse(null);
        if (part == null) {
            return Optional.empty();
        }

        return Optional.of(new RelayRoomResultPartResponse(part, textOrNull(partNode.path("drawerUserUuid")),
            textOrNull(partNode.path("drawerNickname"))));
    }

    private Optional<RelayDrawingPart> parsePart(String value) {
        try {
            return Optional.of(RelayDrawingPart.valueOf(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
