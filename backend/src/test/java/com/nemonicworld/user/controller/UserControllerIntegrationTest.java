package com.nemonicworld.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.domain.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Sql(statements = "DELETE FROM app_user")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createAnonymousUserReturnsCreatedResponseAndPersistsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous").header(HttpHeaders.USER_AGENT, "MangoApp/1.0"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("익명 사용자 UUID 발급 성공"))
            .andExpect(jsonPath("$.data.nickname").value(AppUser.ANONYMOUS_NICKNAME))
            .andExpect(jsonPath("$.data.createdAt").exists()).andReturn();

        JsonNode data = readData(result);
        UUID userUuid = UUID.fromString(data.path("userUuid").asText());
        AppUser savedUser = userRepository.findById(userUuid).orElseThrow();

        assertThat(savedUser.getNickname()).isEqualTo(AppUser.ANONYMOUS_NICKNAME);
        assertThat(savedUser.getUserAgent()).isEqualTo("MangoApp/1.0");
        assertThat(savedUser.getBirthday()).isNull();
        assertThat(savedUser.getBirthtime()).isNull();
        assertThat(savedUser.getCreatedAt()).isEqualTo(LocalDateTime.parse(data.path("createdAt").asText()));
        assertThat(savedUser.getLastSeenAt()).isEqualTo(savedUser.getCreatedAt());
        assertThat(savedUser.getUpdatedAt()).isEqualTo(savedUser.getCreatedAt());
    }

    @Test
    void createAnonymousUserIssuesDifferentUuidEveryCall() throws Exception {
        UUID firstUserUuid = createAnonymousUser("MangoApp/1.0");
        UUID secondUserUuid = createAnonymousUser("MangoApp/1.0");

        assertThat(firstUserUuid).isNotEqualTo(secondUserUuid);
        assertThat(userRepository.existsById(firstUserUuid)).isTrue();
        assertThat(userRepository.existsById(secondUserUuid)).isTrue();
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void createAnonymousUserUsesUnknownWhenUserAgentIsMissingOrBlank() throws Exception {
        UUID missingUserAgentUserUuid = createAnonymousUserWithoutUserAgent();
        UUID blankUserAgentUserUuid = createAnonymousUser(" ");

        assertThat(userRepository.findById(missingUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
        assertThat(userRepository.findById(blankUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
    }

    private UUID createAnonymousUser(String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous").header(HttpHeaders.USER_AGENT, userAgent))
            .andExpect(status().isCreated()).andReturn();

        return UUID.fromString(readData(result).path("userUuid").asText());
    }

    private UUID createAnonymousUserWithoutUserAgent() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous")).andExpect(status().isCreated()).andReturn();

        return UUID.fromString(readData(result).path("userUuid").asText());
    }

    private JsonNode readData(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();

        return objectMapper.readTree(content).path("data");
    }
}
