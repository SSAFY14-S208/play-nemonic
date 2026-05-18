package com.nemonicworld.gms.controller;

import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptPreviewRequest;
import com.nemonicworld.gms.dto.request.GmsPromptTestRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptCurrentResponse;
import com.nemonicworld.gms.dto.response.GmsPromptListResponse;
import com.nemonicworld.gms.dto.response.GmsPromptPreviewResponse;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.service.GmsPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/gms/prompts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.GMS_PROMPT, description = OpenApiTags.GMS_PROMPT_DESCRIPTION)
public class GmsPromptController {

    private static final String FEATURE_TYPE_PARAMETER_DESCRIPTION = "기능 타입: fortune, sticker";
    private static final String STATUS_PARAMETER_DESCRIPTION = "활성 상태 필터: active, not_active, all. 미전달 시 전체 조회";
    private static final String LIST_DESCRIPTION = "관리자가 삭제되지 않은 GMS 프롬프트를 검색하고 active/not_active 상태로 필터링합니다.";
    private static final String CURRENT_DESCRIPTION = "featureType 기준으로 실제 서비스에서 현재 사용 중인 프롬프트를 조회합니다. "
        + "fortune은 active DB 프롬프트가 없으면 기본 프롬프트를 반환합니다.";
    private static final String ACTIVATE_DESCRIPTION = "동일 featureType의 기존 active 프롬프트를 비활성화하고 "
        + "선택한 프롬프트를 현재 사용 중 상태로 전환합니다.";
    private static final String TEST_DESCRIPTION = "저장된 프롬프트 본문과 샘플 사주로 미리보기 결과를 생성합니다. "
        + "결과물, 갤러리, MinIO 객체는 저장하지 않습니다.";
    private static final String CREATE_REQUEST_EXAMPLE = """
        {
          "name": "오늘의 운세 기본 프롬프트 v2",
          "content": "만세력 결과를 바탕으로 오늘의 운세를 JSON 형식으로 생성한다.",
          "featureType": "fortune"
        }
        """;
    private static final String UPDATE_REQUEST_EXAMPLE = """
        {
          "name": "오늘의 운세 기본 프롬프트 v3",
          "content": "운세 결과에 postitLine을 반드시 포함한다."
        }
        """;
    private static final String PREVIEW_REQUEST_EXAMPLE = """
        {
          "featureType": "fortune",
          "content": "샘플 사주를 바탕으로 오늘의 운세를 생성한다.",
          "sampleSaju": {
            "calendarType": "solar",
            "yearPillar": "임신",
            "monthPillar": "경술",
            "dayPillar": "계유",
            "hourPillar": "을묘",
            "dayMasterElement": "수",
            "dayBranchElement": "금",
            "dayMasterYinYang": "음",
            "dayBranchYinYang": "음"
          }
        }
        """;
    private static final String TEST_REQUEST_EXAMPLE = """
        {
          "sampleSaju": {
            "calendarType": "solar",
            "yearPillar": "임신",
            "monthPillar": "경술",
            "dayPillar": "계유",
            "hourPillar": "을묘",
            "dayMasterElement": "수",
            "dayBranchElement": "금",
            "dayMasterYinYang": "음",
            "dayBranchYinYang": "음"
          }
        }
        """;
    private static final String PROMPT_NOT_FOUND_EXAMPLE = """
        {
          "success": false,
          "message": "GMS prompt was not found."
        }
        """;
    private static final String CREATE_SUCCESS_MESSAGE = "GMS 프롬프트 생성 성공";
    private static final String DETAIL_SUCCESS_MESSAGE = "GMS 프롬프트 상세 조회 성공";
    private static final String LIST_SUCCESS_MESSAGE = "GMS 프롬프트 목록 조회 성공";
    private static final String CURRENT_SUCCESS_MESSAGE = "현재 GMS 프롬프트 조회 성공";
    private static final String PREVIEW_SUCCESS_MESSAGE = "GMS 프롬프트 미리보기 성공";
    private static final String TEST_SUCCESS_MESSAGE = "GMS 프롬프트 테스트 성공";
    private static final String UPDATE_SUCCESS_MESSAGE = "GMS 프롬프트 수정 성공";
    private static final String DELETE_SUCCESS_MESSAGE = "GMS 프롬프트 삭제 성공";
    private static final String ACTIVATE_SUCCESS_MESSAGE = "GMS 프롬프트 활성화 성공";

    private final GmsPromptService gmsPromptService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public GmsPromptController(GmsPromptService gmsPromptService, AdminClientInfoResolver adminClientInfoResolver) {
        this.gmsPromptService = gmsPromptService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/preview")
    @Operation(summary = "GMS 프롬프트 미리보기", description = "관리자가 저장 전 후보 프롬프트와 샘플 사주로 운세 결과와 카드 미리보기 이미지를 확인합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = GmsPromptPreviewRequest.class), examples = @ExampleObject(name = "저장 전 후보 프롬프트 미리보기", value = PREVIEW_REQUEST_EXAMPLE)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 미리보기 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GMS 미리보기 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<GmsPromptPreviewResponse>> previewPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody GmsPromptPreviewRequest request) {
        GmsPromptPreviewResponse response = gmsPromptService.previewPrompt(adminPrincipal, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PREVIEW_SUCCESS_MESSAGE, response));
    }

    @PostMapping
    @Operation(summary = "GMS 프롬프트 생성", description = "관리자가 새 GMS 프롬프트 버전을 저장합니다. 생성 직후 상태는 not_active입니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = GmsPromptCreateRequest.class), examples = @ExampleObject(name = "오늘의 운세 프롬프트 생성", value = CREATE_REQUEST_EXAMPLE)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "GMS 프롬프트 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 프롬프트 이름", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> createPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody GmsPromptCreateRequest request,
        HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.createPrompt(adminPrincipal, request,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping
    @Operation(summary = "GMS 프롬프트 목록 조회", description = LIST_DESCRIPTION)
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "프롬프트 이름 또는 본문 검색어", example = "운세")
    @Parameter(name = "featureType", description = FEATURE_TYPE_PARAMETER_DESCRIPTION, example = "fortune")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = STATUS_PARAMETER_DESCRIPTION, example = "active")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF)})
    public ResponseEntity<ApiResponse<GmsPromptListResponse>> getPrompts(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "featureType", required = false) String featureType,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", required = false) String page,
        @RequestParam(name = "size", required = false) String size) {
        GmsPromptListResponse response = gmsPromptService.getPrompts(adminPrincipal, keyword, featureType, status, page,
            size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/current")
    @Operation(summary = "현재 GMS 프롬프트 조회", description = CURRENT_DESCRIPTION)
    @Parameter(name = "featureType", required = true, description = FEATURE_TYPE_PARAMETER_DESCRIPTION)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 GMS 프롬프트 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "현재 GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptCurrentResponse>> getCurrentPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "featureType") String featureType) {
        GmsPromptCurrentResponse response = gmsPromptService.getCurrentPrompt(adminPrincipal, featureType);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CURRENT_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 상세 조회", description = "관리자가 GMS 프롬프트 상세 정보와 active/not_active 상태를 조회합니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "GMS 프롬프트 ID", example = "5")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> getPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId) {
        GmsPromptResponse response = gmsPromptService.getPrompt(adminPrincipal, promptId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    @PatchMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 수정", description = "관리자가 GMS 프롬프트 이름, 본문, 기능 타입을 수정합니다. active 프롬프트는 기능 타입을 변경할 수 없습니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "GMS 프롬프트 ID", example = "5")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = GmsPromptUpdateRequest.class), examples = @ExampleObject(name = "프롬프트 일부 필드 수정", value = UPDATE_REQUEST_EXAMPLE)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 프롬프트 이름", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> updatePrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        @Valid @RequestBody GmsPromptUpdateRequest request, HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.updatePrompt(adminPrincipal, promptId, request,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(UPDATE_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{promptId}")
    @Operation(summary = "GMS 프롬프트 삭제", description = "관리자가 GMS 프롬프트를 soft delete합니다. active 프롬프트를 삭제하면 현재 프롬프트 상태도 비워집니다.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "GMS 프롬프트 ID", example = "5")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<Void>> deletePrompt(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("promptId") Long promptId, HttpServletRequest servletRequest) {
        gmsPromptService.deletePrompt(adminPrincipal, promptId, adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, null));
    }

    @PostMapping("/{promptId}/activate")
    @Operation(summary = "GMS 프롬프트 활성화", description = ACTIVATE_DESCRIPTION)
    @Parameter(name = "promptId", required = true, description = "활성화할 GMS 프롬프트 ID", example = "5")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 활성화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> activatePrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.activatePrompt(adminPrincipal, promptId,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ACTIVATE_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{promptId}/test")
    @Operation(summary = "저장된 GMS 프롬프트 테스트", description = TEST_DESCRIPTION)
    @Parameter(name = "promptId", required = true, description = "테스트할 GMS 프롬프트 ID", example = "5")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = GmsPromptTestRequest.class), examples = @ExampleObject(name = "저장된 프롬프트 테스트", value = TEST_REQUEST_EXAMPLE)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS 프롬프트 테스트 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 본문 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS 프롬프트 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GMS 프롬프트 테스트 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<GmsPromptPreviewResponse>> testPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        @Valid @RequestBody GmsPromptTestRequest request) {
        GmsPromptPreviewResponse response = gmsPromptService.testPrompt(adminPrincipal, promptId, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(TEST_SUCCESS_MESSAGE, response));
    }
}
