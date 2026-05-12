package com.nemonicworld.clientlog.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.clientlog.dto.request.ClientLogIngestRequest;
import com.nemonicworld.clientlog.dto.response.ClientLogIngestResponse;
import com.nemonicworld.clientlog.service.ClientLogService;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = OpenApiTags.CLIENT_LOG, description = OpenApiTags.CLIENT_LOG_DESCRIPTION)
public class ClientLogController {

    private static final String INGEST_SUCCESS_MESSAGE = "클라이언트 로그 수집 성공";

    private final ClientLogService clientLogService;

    public ClientLogController(ClientLogService clientLogService) {
        this.clientLogService = clientLogService;
    }

    @ResponseBody
    @PostMapping("/api/logs/client")
    @Operation(summary = "클라이언트 로그 수집", description = "프론트엔드에서 전송한 표준 로그 이벤트 묶음을 stdout 로그 파이프라인으로 전달합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "클라이언트 로그 수집 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "로그 요청 본문 크기 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.CLIENT_LOG_PAYLOAD_TOO_LARGE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "클라이언트 로그 전송 한도 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.CLIENT_LOG_RATE_LIMITED)))})
    public ResponseEntity<ApiResponse<ClientLogIngestResponse>> ingest(
        @Valid @RequestBody ClientLogIngestRequest request, HttpServletRequest servletRequest) {
        ClientLogIngestResponse response = clientLogService.ingest(request, servletRequest);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(INGEST_SUCCESS_MESSAGE, response));
    }
}
