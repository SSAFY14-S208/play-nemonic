package com.nemonicworld.clientlog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.support.IntegrationTest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "nemonic.client-log.allowed-origins=http://localhost:3000")
class ClientLogControllerIntegrationTest {

    private static final String CLIENT_LOG_ENDPOINT = "/api/logs/client";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestAcceptsValidClientEventAndEmitsStructuredLog(CapturedOutput output) throws Exception {
        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.10").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(validEvent("page_view"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.acceptedCount").value(1)).andExpect(jsonPath("$.data.droppedCount").value(0))
            .andExpect(jsonPath("$.data.systemRoutedCount").value(0));

        JsonNode log = findLog(output, "page_view");
        assertThat(log.path("service").asText()).isEqualTo("client-web");
        assertThat(log.path("log_type").asText()).isEqualTo("client_event");
        assertThat(log.path("trace_id").asText()).isEqualTo("trace-page-view");
        assertThat(log.path("metadata").path("client_timestamp").asText()).isEqualTo("2026-05-12T10:00:00+09:00");
    }

    @Test
    void ingestAcceptsClientLifecycleEventsWithoutTraceId(CapturedOutput output) throws Exception {
        String events = """
            {
              "@timestamp": "2026-05-12T16:01:29.104+09:00",
              "level": "INFO",
              "service": "client-web",
              "event_name": "client_alive",
              "uuid": "fe091d62-90a5-4b77-aa43-9fc79bc4230d",
              "session_id": "a7f98bb0-d18e-46e1-b62a-067df0872a98",
              "path": "/relay-drawing/QFFGAA",
              "metadata": {
                "viewport": {
                  "width": 616,
                  "height": 956
                },
                "platform": "desktop",
                "locale": "ko-KR",
                "network": "4g",
                "time_in_session_ms": 30176
              }
            },
            {
              "@timestamp": "2026-05-12T16:01:53.280+09:00",
              "level": "INFO",
              "service": "client-web",
              "event_name": "session_end",
              "uuid": "fe091d62-90a5-4b77-aa43-9fc79bc4230d",
              "session_id": "a7f98bb0-d18e-46e1-b62a-067df0872a98",
              "path": "/relay-drawing/QFFGAA",
              "metadata": {
                "viewport": {
                  "width": 616,
                  "height": 956
                },
                "platform": "desktop",
                "locale": "ko-KR",
                "network": "4g",
                "last_path": "/relay-drawing/QFFGAA"
              }
            }
            """;

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.19").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(events)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.acceptedCount").value(2)).andExpect(jsonPath("$.data.droppedCount").value(0))
            .andExpect(jsonPath("$.data.systemRoutedCount").value(0));

        JsonNode aliveLog = findLog(output, "client_alive");
        assertThat(aliveLog.path("trace_id").asText()).isNotBlank();
        assertThat(aliveLog.path("path").asText()).isEqualTo("/relay-drawing/QFFGAA");
        assertThat(aliveLog.path("session_id").asText()).isEqualTo("a7f98bb0-d18e-46e1-b62a-067df0872a98");
        assertThat(aliveLog.path("metadata").path("client_timestamp").asText())
            .isEqualTo("2026-05-12T16:01:29.104+09:00");
        assertThat(aliveLog.path("metadata").path("viewport").path("width").asInt()).isEqualTo(616);
        assertThat(aliveLog.path("metadata").path("time_in_session_ms").asInt()).isEqualTo(30176);

        JsonNode sessionEndLog = findLog(output, "session_end");
        assertThat(sessionEndLog.path("metadata").path("last_path").asText()).isEqualTo("/relay-drawing/QFFGAA");
    }

    @Test
    void ingestRejectsMoreThanMaxEventsPerRequest() throws Exception {
        String events = IntStream.range(0, 201).mapToObj(index -> validEvent("client_alive"))
            .collect(Collectors.joining(","));

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.11").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(events)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.events").value("size must be between 0 and 200"));
    }

    @Test
    void ingestRejectsPayloadLargerThanOneMb() throws Exception {
        String largeMessage = "x".repeat(1_050_000);

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.12").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(validEventWithMessage("page_view", largeMessage))))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("로그 요청 본문은 1MB 이하로 전송해 주세요."));
    }

    @Test
    void ingestDropsDisallowedOriginWithoutEmittingOriginalEvent(CapturedOutput output) throws Exception {
        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, "https://evil.example")
                .header("X-Forwarded-For", "203.0.113.13").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(validEvent("cta_clicked"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(0))
            .andExpect(jsonPath("$.data.droppedCount").value(1));

        assertThat(output).contains("\"event_name\":\"client_log_events_dropped\"")
            .doesNotContain("\"event_name\":\"cta_clicked\"");
    }

    @Test
    void ingestRoutesUnlistedEventToSystemLog(CapturedOutput output) throws Exception {
        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.14").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(validEvent("unknown_client_event"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(0))
            .andExpect(jsonPath("$.data.systemRoutedCount").value(1));

        JsonNode log = findLog(output, "client_log_event_unlisted");
        assertThat(log.path("metadata").path("event_name").asText()).isEqualTo("unknown_client_event");
        assertThat(log.path("metadata").path("reason").asText()).isEqualTo("unlisted_event_name");
    }

    @Test
    void ingestDropsEventMissingRequiredFields(CapturedOutput output) throws Exception {
        String event = """
            {
              "@timestamp": "2026-05-12T10:00:00+09:00",
              "trace_id": "trace-page-view",
              "event_name": "page_view"
            }
            """;

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.15").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(event)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(0))
            .andExpect(jsonPath("$.data.droppedCount").value(1));

        JsonNode log = findLog(output, "client_log_events_dropped");
        assertThat(log.path("metadata").path("reason").asText()).isEqualTo("missing_service");
    }

    @Test
    void ingestDropsKnownBotUserAgent(CapturedOutput output) throws Exception {
        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.USER_AGENT, "Slackbot 1.0").header("X-Forwarded-For", "203.0.113.16")
                .contentType(MediaType.APPLICATION_JSON).content(requestBody(validEvent("page_view"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(0))
            .andExpect(jsonPath("$.data.droppedCount").value(1));

        JsonNode log = findLog(output, "client_log_events_dropped");
        assertThat(log.path("metadata").path("reason").asText()).isEqualTo("bot_user_agent");
    }

    @Test
    void ingestSanitizesPiiBeforeLogging(CapturedOutput output) throws Exception {
        String event = """
            {
              "@timestamp": "2026-05-12T10:00:00+09:00",
              "service": "client-web",
              "trace_id": "trace-js-error",
              "event_name": "js_error",
              "path": "/login?token=raw-token&code=1234",
              "referrer": "https://example.com/?email=user@example.com",
              "error": {
                "type": "Error",
                "message": "failed for user@example.com",
                "stack": "Authorization: Bearer abc.def.ghi"
              }
            }
            """;

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.17").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(event)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));

        JsonNode log = findLog(output, "js_error");
        assertThat(log.toString()).contains("token=[REDACTED]", "code=[REDACTED]", "[REDACTED_EMAIL]",
            "Bearer [REDACTED_TOKEN]");
        assertThat(log.toString()).doesNotContain("raw-token", "1234", "user@example.com", "abc.def.ghi");
    }

    @Test
    void ingestRejectsRateLimitExceeded() throws Exception {
        String events = IntStream.range(0, 101).mapToObj(index -> validEvent("client_alive"))
            .collect(Collectors.joining(","));

        mockMvc
            .perform(post(CLIENT_LOG_ENDPOINT).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.18").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(events)))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("클라이언트 로그 전송 한도를 초과했습니다."));
    }

    private String requestBody(String events) {
        return """
            {
              "events": [
                %s
              ]
            }
            """.formatted(events);
    }

    private String validEvent(String eventName) {
        return validEventWithMessage(eventName, "client event");
    }

    private String validEventWithMessage(String eventName, String message) {
        return """
            {
              "@timestamp": "2026-05-12T10:00:00+09:00",
              "service": "client-web",
              "trace_id": "trace-%s",
              "event_name": "%s",
              "message": "%s",
              "metadata": {
                "path": "/home"
              }
            }
            """.formatted(eventName.replace("_", "-"), eventName, message);
    }

    private JsonNode findLog(CapturedOutput output, String eventName) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            if (line.contains("\"event_name\":\"%s\"".formatted(eventName))) {
                return objectMapper.readTree(line.substring(line.indexOf('{')));
            }
        }

        throw new AssertionError("Structured log not found. eventName=" + eventName);
    }
}
