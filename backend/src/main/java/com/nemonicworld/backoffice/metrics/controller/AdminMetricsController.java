package com.nemonicworld.backoffice.metrics.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryRangeResponse;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsQueryResponse;
import com.nemonicworld.backoffice.metrics.service.AdminMetricsService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/metrics")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.ADMIN, description = OpenApiTags.ADMIN_DESCRIPTION)
public class AdminMetricsController {

    private static final String INVALID_QUERY_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_METRICS_INVALID_PARAM",
          "message": "유효하지 않은 메트릭 파라미터입니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UNKNOWN_TEMPLATE_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_METRICS_UNKNOWN_TEMPLATE",
          "message": "허용되지 않은 메트릭 템플릿입니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String INVALID_TIME_RANGE_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_METRICS_INVALID_TIME_RANGE",
          "message": "유효하지 않은 메트릭 시간 범위입니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UNAUTHORIZED_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_METRICS_UNAUTHORIZED",
          "message": "관리자 인증이 필요합니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UPSTREAM_ERROR_EXAMPLE = """
        {
          "success": false,
          "code": "PROMETHEUS_UPSTREAM_ERROR",
          "message": "메트릭 업스트림에 연결할 수 없습니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String TIMEOUT_EXAMPLE = """
        {
          "success": false,
          "code": "PROMETHEUS_TIMEOUT",
          "message": "메트릭 업스트림 응답 시간이 초과되었습니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;

    private final AdminMetricsService adminMetricsService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AdminMetricsController(AdminMetricsService adminMetricsService,
        AdminClientInfoResolver adminClientInfoResolver) {
        this.adminMetricsService = adminMetricsService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/query")
    @Operation(summary = "백오피스 메트릭 즉시 조회", description = "허용된 PromQL 템플릿 id와 파라미터를 지정해 Prometheus 단일 시점 값을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메트릭 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 메트릭 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "invalid-param", value = INVALID_QUERY_EXAMPLE),
            @ExampleObject(name = "unknown-template", value = UNKNOWN_TEMPLATE_EXAMPLE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Prometheus 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "Prometheus 응답 시간 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = TIMEOUT_EXAMPLE)))})
    public ResponseEntity<MetricsQueryResponse> query(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        MetricsQueryResponse response = adminMetricsService.query(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/query-range")
    @Operation(summary = "백오피스 메트릭 시계열 조회", description = "허용된 PromQL 템플릿 id와 파라미터, 시간 범위, step을 지정해 Prometheus 시계열을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메트릭 시계열 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 메트릭 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "invalid-param", value = INVALID_QUERY_EXAMPLE),
            @ExampleObject(name = "invalid-time-range", value = INVALID_TIME_RANGE_EXAMPLE),
            @ExampleObject(name = "unknown-template", value = UNKNOWN_TEMPLATE_EXAMPLE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Prometheus 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "Prometheus 응답 시간 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = TIMEOUT_EXAMPLE)))})
    public ResponseEntity<MetricsQueryRangeResponse> queryRange(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        MetricsQueryRangeResponse response = adminMetricsService.queryRange(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
