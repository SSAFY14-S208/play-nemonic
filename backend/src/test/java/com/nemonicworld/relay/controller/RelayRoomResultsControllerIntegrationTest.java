package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RelayRoomResultsControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String MINIO_PUBLIC_URL = "http://localhost:9000/nemonic-local/";
    private static final String ROOM_CODE = "AB3K9Q";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 17, 0).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        prepareArtifactTables();
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");

        valueOperations = createValueOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
    }

    @Test
    void getRelayRoomResultsReturnsOwnedFinalImagesAndPartMetadata() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Mango");
        insertRelayResult(ownerUuid, 1, NOW.minusSeconds(2), false);
        insertRelayResult(ownerUuid, 0, NOW.minusSeconds(1), false);

        String responseBody = mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/results", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                ownerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 결과 조회 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE)).andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.resultCount").value(2))
            .andExpect(jsonPath("$.data.results[0].canvasIndex").value(0))
            .andExpect(jsonPath("$.data.results[0].thumbnailUrl").value(publicUrl("relay/results/0/thumbnail.png")))
            .andExpect(jsonPath("$.data.results[0].contentUrl").value(publicUrl("relay/results/0/original.png")))
            .andExpect(jsonPath("$.data.results[0].parts[0].part").value("FACE"))
            .andExpect(jsonPath("$.data.results[0].parts[0].drawerUserUuid").value(ownerUuid.toString()))
            .andExpect(jsonPath("$.data.results[0].parts[0].drawerNickname").value("Mango"))
            .andExpect(jsonPath("$.data.results[0].parts[1].part").value("BODY"))
            .andExpect(jsonPath("$.data.results[0].parts[2].part").value("LEGS"))
            .andExpect(jsonPath("$.data.results[1].canvasIndex").value(1)).andReturn().getResponse()
            .getContentAsString();

        assertThat(responseBody).doesNotContain("relay/tmp");
    }

    @Test
    void getRelayRoomResultsReturnsReadyFalseBeforeResultsAreCreated() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Mango");
        storeRoom(new RelayRoomState(ROOM_CODE, RelayRoomStatus.FINALIZING, ownerUuid.toString(), 45, 2, 6,
            RelayDrawingPart.LEGS,
            List.of(new RelayRoomParticipant(ownerUuid.toString(), "Mango", true, 0, true, null, NOW.minusMinutes(10))),
            List.of(), NOW.minusMinutes(2), NOW.minusMinutes(1), NOW.minusMinutes(10), NOW.minusMinutes(20), NOW));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/results", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                ownerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ready").value(false))
            .andExpect(jsonPath("$.data.roomStatus").value("FINALIZING"))
            .andExpect(jsonPath("$.data.resultCount").value(0)).andExpect(jsonPath("$.data.results").isArray())
            .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void getRelayRoomResultsRejectsUserWithoutActiveGallery() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Mango");
        UUID otherUuid = createExistingUserWithNickname("Peach");
        insertRelayResult(ownerUuid, 0, NOW, false);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/results", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                otherUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 결과를 조회할 권한이 없습니다."));
    }

    @Test
    void getRelayRoomResultsExcludesSoftDeletedGalleryRows() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Mango");
        insertRelayResult(ownerUuid, 0, NOW, true);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/results", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                ownerUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 결과를 조회할 권한이 없습니다."));
    }

    @Test
    void getRelayRoomResultsRejectsInvalidUuid() throws Exception {
        mockMvc.perform(
            get("/api/v1/relay/rooms/{roomCode}/results", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    private void prepareArtifactTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id UUID PRIMARY KEY,
                kind VARCHAR(32) NOT NULL,
                source_room_id VARCHAR(64) NULL,
                thumbnail_url VARCHAR(200) NOT NULL,
                meta VARCHAR(1000) NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS gallery (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
    }

    private UUID createExistingUserWithNickname(String nickname) {
        UUID userUuid = UUID.randomUUID();
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", NOW.minusDays(1));
        appUser.updateNickname(nickname, NOW.minusHours(1));
        userRepository.saveAndFlush(appUser);

        return userUuid;
    }

    private void insertRelayResult(UUID ownerUuid, int canvasIndex, LocalDateTime createdAt, boolean deleted) {
        UUID artifactId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, artifactId, "relay_drawing", ROOM_CODE, "relay/results/%d/thumbnail.png".formatted(canvasIndex),
            meta(ownerUuid, canvasIndex), createdAt, createdAt);
        jdbcTemplate.update("INSERT INTO relay_drawing_artifact (artifact_id, combined_preview_url) VALUES (?, ?)",
            artifactId, "relay/results/%d/original.png".formatted(canvasIndex));
        jdbcTemplate.update("INSERT INTO gallery (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), ownerUuid, artifactId, deleted ? createdAt.plusSeconds(1) : null);
    }

    private String meta(UUID ownerUuid, int canvasIndex) {
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
            """.formatted(canvasIndex, ROOM_CODE, ownerUuid, UUID.randomUUID(), UUID.randomUUID());
    }

    private void storeRoom(RelayRoomState roomState) throws Exception {
        given(valueOperations.get("relay:room:%s".formatted(ROOM_CODE)))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private String publicUrl(String objectKey) {
        return MINIO_PUBLIC_URL + objectKey;
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
