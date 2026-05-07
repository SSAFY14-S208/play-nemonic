package com.nemonicworld.gms.controller;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.service.GmsPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/gms/prompts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "GMS Prompts", description = "Backoffice GMS prompt management API")
public class GmsPromptController {

    private static final String DELETE_SUCCESS_MESSAGE = "GMS 프롬프트 삭제 성공";
    private static final String PROMPT_NOT_FOUND_EXAMPLE = """
        {
          "success": false,
          "message": "GMS 프롬프트를 찾을 수 없습니다."
        }
        """;

    private static final String CREATE_SUCCESS_MESSAGE = "GMS 프롬프트 생성 성공";
    private static final String DETAIL_SUCCESS_MESSAGE = "GMS 프롬프트 상세 조회 성공";
    private static final String UPDATE_SUCCESS_MESSAGE = "GMS 프롬프트 수정 성공";

    private final GmsPromptService gmsPromptService;

    public GmsPromptController(GmsPromptService gmsPromptService) {
        this.gmsPromptService = gmsPromptService;
    }

    @PostMapping
    @Operation(summary = "GMS 프롬프트 생성", description = "백오피스 관리자가 새 GMS 프롬프트를 생성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "GMS 프롬프트 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 프롬프트 코드", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> createPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody GmsPromptCreateRequest request) {
        GmsPromptResponse response = gmsPromptService.createPrompt(adminPrincipal, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 상세 조회", description = "백오피스 관리자가 GMS 프롬프트 상세 정보를 조회합니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "조회할 GMS 프롬프트 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> getPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId) {
        GmsPromptResponse response = gmsPromptService.getPrompt(adminPrincipal, promptId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 삭제", description = "백오피스 관리자가 GMS 프롬프트를 삭제합니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "삭제할 GMS 프롬프트 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<Void>> deletePrompt(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("promptId") Long promptId) {
        gmsPromptService.deletePrompt(adminPrincipal, promptId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, null));
    }

    @PatchMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 수정", description = "백오피스 관리자가 GMS 프롬프트를 수정합니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "수정할 GMS 프롬프트 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.ADMIN_UNAUTHORIZED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 프롬프트 이름", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> updatePrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        @Valid @RequestBody GmsPromptUpdateRequest request) {
        GmsPromptResponse response = gmsPromptService.updatePrompt(adminPrincipal, promptId, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(UPDATE_SUCCESS_MESSAGE, response));
    }
}
