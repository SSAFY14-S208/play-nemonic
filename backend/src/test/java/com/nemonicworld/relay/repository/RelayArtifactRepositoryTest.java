package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.relay.service.finalization.RelayFinalizationArtifactResult;
import com.nemonicworld.support.AbstractIntegrationTest;
import com.nemonicworld.support.ArtifactGalleryTestFixture;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RelayArtifactRepositoryTest extends AbstractIntegrationTest {

    private static final String ROOM_CODE = "AB3K9Q";

    @Autowired
    private RelayArtifactRepository relayArtifactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareTables() {
        new ArtifactGalleryTestFixture(jdbcTemplate).resetRelayArtifactTables();
    }

    @Test
    void saveRelayDrawingResultsCreatesArtifactSubtypeAndGalleryRowsForEveryParticipant() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        UUID participantC = UUID.randomUUID();
        List<RelayFinalizationArtifactResult> artifacts = List.of(artifact(0), artifact(1), artifact(2));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        relayArtifactRepository.saveRelayDrawingResults(ROOM_CODE, artifacts,
            List.of(participantA.toString(), participantB.toString(), participantC.toString()), now);

        assertThat(countRows("artifact")).isEqualTo(3);
        assertThat(countRows("relay_drawing_artifact")).isEqualTo(3);
        assertThat(countRows("gallery")).isEqualTo(9);
        assertThat(countGalleryRows(participantA)).isEqualTo(3);
        assertThat(countGalleryRows(participantB)).isEqualTo(3);
        assertThat(countGalleryRows(participantC)).isEqualTo(3);
        assertThat(findArtifactIdsByUser(participantA)).containsExactlyInAnyOrderElementsOf(artifactIds(artifacts));
        assertThat(findArtifactIdsByUser(participantB)).containsExactlyInAnyOrderElementsOf(artifactIds(artifacts));
        assertThat(findArtifactIdsByUser(participantC)).containsExactlyInAnyOrderElementsOf(artifactIds(artifacts));
        assertThat(findThumbnailUrl(artifacts.get(0).artifactId())).isEqualTo(artifacts.get(0).thumbnailObjectKey());
        assertThat(findCombinedPreviewUrl(artifacts.get(0).artifactId()))
            .isEqualTo(artifacts.get(0).originalObjectKey());
        assertThat(findMeta(artifacts.get(0).artifactId())).contains("\"canvasIndex\":0");
    }

    @Test
    void findRelayArtifactsBySourceRoomIdReturnsCanvasIndexedRelayArtifacts() {
        List<RelayFinalizationArtifactResult> artifacts = List.of(artifact(0), artifact(1));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        relayArtifactRepository.saveRelayDrawingResults(ROOM_CODE, artifacts, List.of(UUID.randomUUID().toString()),
            now);

        List<RelayFinalizationArtifactResult> found = relayArtifactRepository
            .findRelayArtifactsBySourceRoomId(ROOM_CODE);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(RelayFinalizationArtifactResult::canvasIndex).containsExactly(0, 1);
        assertThat(found).extracting(RelayFinalizationArtifactResult::originalObjectKey)
            .containsExactly(artifacts.get(0).originalObjectKey(), artifacts.get(1).originalObjectKey());
    }

    @Test
    void findActiveRelayResultsByRoomCodeAndUserUuidReturnsOnlyActiveOwnedGalleryRows() {
        UUID owner = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        List<RelayFinalizationArtifactResult> artifacts = List.of(artifact(0), artifact(1));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        relayArtifactRepository.saveRelayDrawingResults(ROOM_CODE, artifacts,
            List.of(owner.toString(), otherUser.toString()), now);
        softDeleteGalleryRow(owner, artifacts.get(1).artifactId(), now.plusSeconds(1));

        List<RelayResultArtifactRow> found = relayArtifactRepository
            .findActiveRelayResultsByRoomCodeAndUserUuid(ROOM_CODE, owner);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).galleryId()).isNotNull();
        assertThat(found.get(0).artifactId()).isEqualTo(artifacts.get(0).artifactId());
        assertThat(found.get(0).thumbnailUrl()).isEqualTo(artifacts.get(0).thumbnailObjectKey());
        assertThat(found.get(0).contentUrl()).isEqualTo(artifacts.get(0).originalObjectKey());
        assertThat(found.get(0).createdAt()).isEqualTo(now);
        assertThat(relayArtifactRepository.countRelayResultsByRoomCode(ROOM_CODE)).isEqualTo(2);
    }

    @Test
    void findReferencedRelayResultObjectKeysReturnsReferencedOriginalAndThumbnailKeys() {
        RelayFinalizationArtifactResult artifact = artifact(0);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        relayArtifactRepository.saveRelayDrawingResults(ROOM_CODE, List.of(artifact),
            List.of(UUID.randomUUID().toString()), now);

        Set<String> referencedObjectKeys = relayArtifactRepository.findReferencedRelayResultObjectKeys(
            Set.of(artifact.originalObjectKey(), artifact.thumbnailObjectKey(), "relay/results/orphan/original.png"));

        assertThat(referencedObjectKeys).containsExactlyInAnyOrder(artifact.originalObjectKey(),
            artifact.thumbnailObjectKey());
    }

    private RelayFinalizationArtifactResult artifact(int canvasIndex) {
        UUID artifactId = UUID.randomUUID();
        return new RelayFinalizationArtifactResult(artifactId, canvasIndex,
            "relay/results/%s/original.png".formatted(artifactId),
            "relay/results/%s/thumbnail.png".formatted(artifactId),
            "{\"canvasIndex\":%d,\"roomCode\":\"%s\",\"parts\":[\"FACE\",\"BODY\",\"LEGS\"]}".formatted(canvasIndex,
                ROOM_CODE));
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Long.class);
    }

    private long countGalleryRows(UUID userUuid) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gallery WHERE user_id = ?", Long.class, userUuid);
    }

    private List<UUID> findArtifactIdsByUser(UUID userUuid) {
        return jdbcTemplate.query("SELECT artifact_id FROM gallery WHERE user_id = ? ORDER BY artifact_id",
            (resultSet, rowNumber) -> resultSet.getObject("artifact_id", UUID.class), userUuid);
    }

    private List<UUID> artifactIds(List<RelayFinalizationArtifactResult> artifacts) {
        return artifacts.stream().map(RelayFinalizationArtifactResult::artifactId).toList();
    }

    private String findThumbnailUrl(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT thumbnail_url FROM artifact WHERE id = ?", String.class, artifactId);
    }

    private String findCombinedPreviewUrl(UUID artifactId) {
        return jdbcTemplate.queryForObject(
            "SELECT combined_preview_url FROM relay_drawing_artifact WHERE artifact_id = ?", String.class, artifactId);
    }

    private String findMeta(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT meta FROM artifact WHERE id = ?", String.class, artifactId);
    }

    private void softDeleteGalleryRow(UUID userUuid, UUID artifactId, LocalDateTime deletedAt) {
        jdbcTemplate.update("UPDATE gallery SET deleted_at = ? WHERE user_id = ? AND artifact_id = ?", deletedAt,
            userUuid, artifactId);
    }
}
