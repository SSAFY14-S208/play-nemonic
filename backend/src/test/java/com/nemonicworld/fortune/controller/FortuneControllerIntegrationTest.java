package com.nemonicworld.fortune.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 오늘의 운세 생성 가능 여부 조회 API를 통합 검증합니다.
 */
class FortuneControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void prepareFortuneTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS fortune_artifact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS artifact");
        jdbcTemplate.execute("""
            CREATE TABLE artifact (
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
            CREATE TABLE fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL,
                fortune_image_url VARCHAR(200) NOT NULL,
                user_id UUID NOT NULL,
                fortune_date DATE NOT NULL
            )
            """);
    }

    /**
     * 오늘 생성된 운세가 없으면 생성 가능 상태를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsAvailableWhenUserHasNoFortuneToday() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("오늘의 운세 생성 가능 여부 조회 성공"))
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.fortuneDate").value(today.toString()))
            .andExpect(jsonPath("$.data.todayFortuneId").value(nullValue()))
            .andExpect(jsonPath("$.data.createdAt").value(nullValue()))
            .andExpect(jsonPath("$.data.nextAvailableAt").value(nextAvailableAt(today)));
    }

    /**
     * 오늘 생성된 운세가 있으면 재생성 불가와 기존 fortuneId를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsUnavailableWhenUserAlreadyHasFortuneToday() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID fortuneId = insertFortuneArtifact(userUuid, today, createdAt);

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.available").value(false))
            .andExpect(jsonPath("$.data.fortuneDate").value(today.toString()))
            .andExpect(jsonPath("$.data.todayFortuneId").value(fortuneId.toString()))
            .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.data.nextAvailableAt").value(nextAvailableAt(today)));
    }

    /**
     * 다른 사용자가 오늘 운세를 생성했더라도 현재 사용자의 생성 가능 여부에는 영향을 주지 않습니다.
     */
    @Test
    void getTodayAvailabilityIgnoresOtherUsersFortune() throws Exception {
        UUID userUuid = createExistingUser();
        UUID otherUserUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);

        insertFortuneArtifact(otherUserUuid, today, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.todayFortuneId").value(nullValue()));
    }

    /**
     * UUID 헤더가 없거나 형식이 올바르지 않으면 400을 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsBadRequestWhenUuidIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/fortune/today/availability")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc.perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    /**
     * UUID 형식은 맞지만 서버에 존재하지 않는 사용자이면 404를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER,
                UUID.randomUUID().toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private UUID insertFortuneArtifact(UUID userUuid, LocalDate fortuneDate, LocalDateTime createdAt) {
        UUID fortuneId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, fortuneId, "fortune", null, "fortune/thumb.png", "{}", Timestamp.valueOf(createdAt),
            Timestamp.valueOf(createdAt));
        jdbcTemplate.update("""
            INSERT INTO fortune_artifact (
                artifact_id, description, fortune_image_url, user_id, fortune_date
            )
            VALUES (?, ?, ?, ?, ?)
            """, fortuneId, "{}", "fortune/card.png", userUuid, fortuneDate);

        return fortuneId;
    }

    private String nextAvailableAt(LocalDate today) {
        return today.plusDays(1).atStartOfDay(KST_ZONE).toOffsetDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }
}
