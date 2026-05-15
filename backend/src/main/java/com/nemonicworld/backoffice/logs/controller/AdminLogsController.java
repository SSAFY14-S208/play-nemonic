package com.nemonicworld.backoffice.logs.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.backoffice.logs.dto.response.LogsFieldSummaryResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsHistogramResponse;
import com.nemonicworld.backoffice.logs.dto.response.LogsSearchResponse;
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
          "message": "Invalid logs query.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UNAUTHORIZED_EXAMPLE = """
        {
          "success": false,
          "code": "ADMIN_LOGS_UNAUTHORIZED",
          "message": "Admin authentication is required.",
          "timestamp": "2026-05-15T00:00:00Z"
        }
        """;
    private static final String UPSTREAM_ERROR_EXAMPLE = """
        {
          "success": false,
          "code": "OPENSEARCH_UPSTREAM_ERROR",
          "message": "Log search upstream is unavailable.",
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
    @Operation(summary = "Backoffice log search", description = "Search whitelisted OpenSearch log indexes.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Log search succeeded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid log search request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Admin authentication required", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch upstream error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsSearchResponse> search(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsSearchResponse response = adminLogsService.search(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/histogram")
    @Operation(summary = "Backoffice log histogram", description = "Read log time buckets and level counts.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Log histogram read succeeded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid log histogram request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Admin authentication required", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch upstream error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsHistogramResponse> histogram(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsHistogramResponse response = adminLogsService.histogram(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping("/field-summary")
    @Operation(summary = "Backoffice log field summary", description = "Read top values for allowed log fields.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Log field summary read succeeded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid log field summary request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = INVALID_QUERY_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Admin authentication required", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenSearch upstream error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = UPSTREAM_ERROR_EXAMPLE)))})
    public ResponseEntity<LogsFieldSummaryResponse> fieldSummary(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestBody JsonNode requestNode, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        LogsFieldSummaryResponse response = adminLogsService.fieldSummary(adminPrincipal, requestNode, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
