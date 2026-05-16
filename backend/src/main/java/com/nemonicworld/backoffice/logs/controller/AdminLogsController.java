package com.nemonicworld.backoffice.logs.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.backoffice.logs.dto.response.LogsCompositeBucketsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsDistinctCountResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsFilteredMetricsResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithMetricResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsTermsWithSubsResponse;
import com.nemonicworld.backoffice.logs.service.AdminLogsService;
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
@RequestMapping("/admin/logs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.ADMIN, description = OpenApiTags.ADMIN_DESCRIPTION)
public class AdminLogsController {

    private static final String INVALID_QUERY_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_LOGS_INVALID_QUERY",
          "message": "유효하지 않은 로그 질의입니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UNAUTHORIZED_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_LOGS_UNAUTHORIZED",
          "message": "관리자 인증이 필요합니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UPSTREAM_ERROR_EXAMPLE = """
        {
          "success": false,
          "code": "OPENSEARCH_UPSTREAM_ERROR",
          "message": "로그 검색 업스트림에 연결할 수 없습니다.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;

    private final AdminLogsService adminLogsService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public AdminLogsController(AdminLogsService adminLogsService, AdminClientInfoResolver adminClientInfoResolver) {
        this.adminLogsService = adminLogsService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/search")
    @Operation(summary = "백오피스 로그 검색", description = "허용된 OpenSearch 로그 인덱스를 검색합니다.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 검색 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 검색 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsSearchResponse> search(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsSearchResponse response = adminLogsService.search(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/histogram")
    @Operation(summary = "백오피스 로그 히스토그램", description = "로그 시간 구간별 레벨별 건수와 선택적 그룹별 분포를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 히스토그램 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 히스토그램 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsHistogramResponse> histogram(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsHistogramResponse response = adminLogsService.histogram(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/field-summary")
    @Operation(summary = "백오피스 로그 필드 요약", description = "허용된 로그 필드의 상위 값을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 필드 요약 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 필드 요약 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsFieldSummaryResponse> fieldSummary(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsFieldSummaryResponse response = adminLogsService.fieldSummary(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/terms-with-subs")
    @Operation(summary = "백오피스 로그 그룹별 서브 필터 집계", description = "groupBy 필드별 버킷에 명명된 서브 필터를 적용해 각 버킷의 분기 카운트를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 그룹 집계 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 집계 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsTermsWithSubsResponse> termsWithSubs(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @RequestBody JsonNode requestNode,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsTermsWithSubsResponse response = adminLogsService.termsWithSubs(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/terms-with-metric")
    @Operation(summary = "백오피스 로그 그룹별 메트릭 집계", description = "groupBy 필드별 버킷에 숫자 메트릭(avg/sum/max/min)을 계산합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 그룹 메트릭 집계 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 집계 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsTermsWithMetricResponse> termsWithMetric(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @RequestBody JsonNode requestNode,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsTermsWithMetricResponse response = adminLogsService.termsWithMetric(adminPrincipal, requestNode,
            clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/composite-buckets")
    @Operation(summary = "백오피스 로그 컴포지트 버킷 집계", description = "두 개의 키 필드로 컴포지트 버킷을 만들어 페이지네이션 가능한 결과를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그 컴포지트 집계 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 집계 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsCompositeBucketsResponse> compositeBuckets(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @RequestBody JsonNode requestNode,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsCompositeBucketsResponse response = adminLogsService.compositeBuckets(adminPrincipal, requestNode,
            clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/filtered-metrics")
    @Operation(summary = "백오피스 로그 분기별 필터 메트릭 집계", description = "이름이 있는 다중 필터별로 매칭 건수와 선택적 메트릭을 한 요청으로 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분기별 필터 메트릭 집계 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 집계 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsFilteredMetricsResponse> filteredMetrics(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @RequestBody JsonNode requestNode,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsFilteredMetricsResponse response = adminLogsService.filteredMetrics(adminPrincipal, requestNode,
            clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/distinct-count")
    @Operation(summary = "백오피스 로그 고유 값 집계", description = "허용된 식별자 필드에 대한 카디널리티 추정값을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "고유 값 집계 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 로그 집계 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch 업스트림 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsDistinctCountResponse> distinctCount(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @RequestBody JsonNode requestNode,
        HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsDistinctCountResponse response = adminLogsService.distinctCount(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
