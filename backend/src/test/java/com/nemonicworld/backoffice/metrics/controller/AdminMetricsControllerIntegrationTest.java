package com.nemonicworld.backoffice.metrics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import com.nemonicworld.backoffice.metrics.service.PrometheusClient;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.support.AbstractReadOnlyIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.support.BackofficeAuthTestFixture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
class AdminMetricsControllerIntegrationTest extends AbstractReadOnlyIntegrationTest {

    private static final long ADMIN_ID = 72L;
    private static final String ADMIN_LOGIN_ID = "metrics-admin";
    private static final String ADMIN_NICKNAME = "Metrics Admin";
    private static final String ADMIN_EMAIL = "metrics-admin@example.com";
    private static final String QUERY_PATH = "/api/v1/admin/metrics/query";
    private static final String QUERY_RANGE_PATH = "/api/v1/admin/metrics/query-range";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Cache<String, Object> adminMetricsCache;

    private AdminUserTestFixture adminUserFixture;

    @MockitoBean
    private PrometheusClient prometheusClient;

    @BeforeEach
    void prepareTables() {
        adminMetricsCache.invalidateAll();
        adminUserFixture = new AdminUserTestFixture(jdbcTemplate);
        adminUserFixture.reset(200);
        insertAdminUser(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME, ADMIN_EMAIL, AdminRole.ADMIN);
    }

    @Test
    void unauthenticatedRequestReturnsMetricsErrorShape() throws Exception {
        mockMvc.perform(post(QUERY_PATH).contentType(MediaType.APPLICATION_JSON).content(validQueryBody()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ADMIN_METRICS_UNAUTHORIZED"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminRequestReturnsForbiddenErrorShape() throws Exception {
        mockMvc.perform(post(QUERY_PATH).contentType(MediaType.APPLICATION_JSON).content(validQueryBody()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ADMIN_METRICS_FORBIDDEN"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void instantQueryReturnsMappedVectorResponse(CapturedOutput output) throws Exception {
        given(prometheusClient.instantQuery(eq("up{job=\"spring-app\"}"), anyLong()))
            .willReturn(objectMapper.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {"metric": {"job": "spring-app", "instance": "app:8080"}, "value": [1747094400, "1"]}
                    ]
                  }
                }
                """));

        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "metrics-query-trace").contentType(MediaType.APPLICATION_JSON)
                .content(validQueryBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultType").value("vector"))
            .andExpect(jsonPath("$.series.length()").value(1))
            .andExpect(jsonPath("$.series[0].labels.job").value("spring-app"))
            .andExpect(jsonPath("$.series[0].value").value(1.0));

        JsonNode auditLog = findAuditLog(output, "admin_metrics_query");
        assertThat(auditLog.path("metadata").path("template").asText()).isEqualTo("service_up_status");
        assertThat(auditLog.path("metadata").path("endpoint").asText()).isEqualTo("query");
        assertThat(auditLog.path("metadata").path("param_keys").toString()).contains("job");
    }

    @Test
    void instantQueryNormalizesNanAndInfiniteValues() throws Exception {
        given(prometheusClient.instantQuery(anyString(), anyLong())).willReturn(objectMapper.readTree("""
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {"metric": {"instance": "a"}, "value": [1747094400, "NaN"]},
                  {"metric": {"instance": "b"}, "value": [1747094400, "+Inf"]},
                  {"metric": {"instance": "c"}, "value": [1747094400, "3.14"]}
                ]
              }
            }
            """));

        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(validQueryBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.series[0].value").doesNotExist())
            .andExpect(jsonPath("$.series[1].value").doesNotExist())
            .andExpect(jsonPath("$.series[2].value").value(3.14));
    }

    @Test
    void rangeQueryReturnsMappedMatrixResponse(CapturedOutput output) throws Exception {
        given(prometheusClient.rangeQuery(anyString(), anyLong(), anyLong(), eq("30s")))
            .willReturn(objectMapper.readTree("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [
                      {
                        "metric": {"service": "api-server"},
                        "values": [[1747094400, "12.3"], [1747094430, "13.1"]]
                      }
                    ]
                  }
                }
                """));

        mockMvc
            .perform(post(QUERY_RANGE_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "metrics-range-trace").contentType(MediaType.APPLICATION_JSON)
                .content(validRangeBody("30s")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resultType").value("matrix"))
            .andExpect(jsonPath("$.interval").value("30s"))
            .andExpect(jsonPath("$.series[0].labels.service").value("api-server"))
            .andExpect(jsonPath("$.series[0].samples.length()").value(2))
            .andExpect(jsonPath("$.series[0].samples[0].value").value(12.3));

        JsonNode auditLog = findAuditLog(output, "admin_metrics_query");
        assertThat(auditLog.path("metadata").path("endpoint").asText()).isEqualTo("query-range");
        assertThat(auditLog.path("metadata").path("step").asText()).isEqualTo("30s");
    }

    @Test
    void unknownTemplateReturnsBadRequest() throws Exception {
        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"template": "bogus_template", "params": {}}
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_METRICS_UNKNOWN_TEMPLATE"));
    }

    @Test
    void invalidParamReturnsBadRequest() throws Exception {
        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"template": "service_up_status", "params": {"job": "bogus"}}
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_METRICS_INVALID_PARAM"));
    }

    @Test
    void rangeQueryRejectsTimeRangeLongerThanThirtyDays() throws Exception {
        mockMvc
            .perform(post(QUERY_RANGE_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "template": "spring_http_rps_by_service",
                      "params": {"window": "5m"},
                      "timeRange": {"from": "2026-04-01T00:00:00Z", "to": "2026-05-15T00:00:00Z"},
                      "step": "30s"
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_METRICS_INVALID_TIME_RANGE"));
    }

    @Test
    void rangeQueryRejectsStepOutsideAllowedRange() throws Exception {
        mockMvc
            .perform(post(QUERY_RANGE_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(validRangeBody("2s")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_METRICS_INVALID_PARAM"));
    }

    @Test
    void upstreamFailureReturnsBadGateway() throws Exception {
        given(prometheusClient.instantQuery(anyString(), anyLong()))
            .willThrow(AdminMetricsException.upstream(new RuntimeException("upstream")));

        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(validQueryBody()))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PROMETHEUS_UPSTREAM_ERROR"));
    }

    @Test
    void timeoutReturnsGatewayTimeout() throws Exception {
        given(prometheusClient.instantQuery(anyString(), anyLong()))
            .willThrow(AdminMetricsException.timeout(new TimeoutException("timeout")));

        mockMvc
            .perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(validQueryBody()))
            .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("PROMETHEUS_TIMEOUT"));
    }

    @Test
    void instantQueryServesCachedResponseWithinTtl() throws Exception {
        given(prometheusClient.instantQuery(anyString(), anyLong())).willReturn(objectMapper.readTree("""
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {"metric": {"job": "spring-app"}, "value": [1747094400, "1"]}
                ]
              }
            }
            """));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(QUERY_PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(validQueryBody())).andExpect(status().isOk());
        }
        // 캐시가 동작했다면 호출은 1번만 발생 (모킹은 항상 같은 응답을 반환하지만 검증은 인보케이션 수로)
        org.mockito.Mockito.verify(prometheusClient, org.mockito.Mockito.times(1)).instantQuery(anyString(), anyLong());
    }

    private void insertAdminUser(long id, String loginId, String nickname, String email, AdminRole role) {
        adminUserFixture.insertEncoded(id, loginId, nickname, email, role);
    }

    private String bearerAccessToken() {
        return BackofficeAuthTestFixture.bearerAccessToken(jwtTokenProvider, ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME,
            ADMIN_EMAIL, AdminRole.ADMIN);
    }

    private String validQueryBody() {
        return """
            {
              "template": "service_up_status",
              "params": {"job": "spring-app"}
            }
            """;
    }

    private String validRangeBody(String step) {
        return """
            {
              "template": "spring_http_rps_by_service",
              "params": {"window": "5m"},
              "timeRange": {"from": "2026-05-14T00:00:00Z", "to": "2026-05-15T00:00:00Z"},
              "step": "%s"
            }
            """.formatted(step);
    }

    private JsonNode findAuditLog(CapturedOutput output, String eventName) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            if (line.contains("\"event_name\":\"%s\"".formatted(eventName))) {
                return objectMapper.readTree(line.substring(line.indexOf('{')));
            }
        }

        throw new AssertionError("Audit log not found. eventName=" + eventName);
    }

}
