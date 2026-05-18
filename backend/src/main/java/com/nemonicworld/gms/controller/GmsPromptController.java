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

    private static final String PROMPT_NOT_FOUND_EXAMPLE = """
        {
          "success": false,
          "message": "GMS prompt was not found."
        }
        """;
    private static final String CREATE_SUCCESS_MESSAGE = "GMS prompt created";
    private static final String DETAIL_SUCCESS_MESSAGE = "GMS prompt detail retrieved";
    private static final String LIST_SUCCESS_MESSAGE = "GMS prompt list retrieved";
    private static final String CURRENT_SUCCESS_MESSAGE = "Current GMS prompt retrieved";
    private static final String PREVIEW_SUCCESS_MESSAGE = "GMS prompt preview created";
    private static final String TEST_SUCCESS_MESSAGE = "GMS prompt test created";
    private static final String UPDATE_SUCCESS_MESSAGE = "GMS prompt updated";
    private static final String DELETE_SUCCESS_MESSAGE = "GMS prompt deleted";
    private static final String ACTIVATE_SUCCESS_MESSAGE = "GMS prompt activated";

    private final GmsPromptService gmsPromptService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public GmsPromptController(GmsPromptService gmsPromptService, AdminClientInfoResolver adminClientInfoResolver) {
        this.gmsPromptService = gmsPromptService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @PostMapping("/preview")
    @Operation(summary = "GMS prompt preview", description = "Tests an unsaved candidate GMS prompt.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt preview created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GMS prompt preview failed", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<GmsPromptPreviewResponse>> previewPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody GmsPromptPreviewRequest request) {
        GmsPromptPreviewResponse response = gmsPromptService.previewPrompt(adminPrincipal, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PREVIEW_SUCCESS_MESSAGE, response));
    }

    @PostMapping
    @Operation(summary = "GMS prompt create", description = "Creates a saved GMS prompt.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "GMS prompt created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicated prompt name", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> createPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @Valid @RequestBody GmsPromptCreateRequest request,
        HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.createPrompt(adminPrincipal, request,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CREATE_SUCCESS_MESSAGE, response));
    }

    @GetMapping
    @Operation(summary = "GMS prompt list", description = "Lists saved non-deleted GMS prompts.")
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "Prompt name or body keyword")
    @Parameter(name = "featureType", in = ParameterIn.QUERY, description = "Prompt feature type")
    @Parameter(name = "status", in = ParameterIn.QUERY, description = "active, not_active, or all")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "Page number", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size", example = "20")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt list retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
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
    @Operation(summary = "Current GMS prompt", description = "Retrieves the prompt currently used by a feature.")
    @Parameter(name = "featureType", in = ParameterIn.QUERY, required = true, description = "Prompt feature type")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current GMS prompt retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Current GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptCurrentResponse>> getCurrentPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "featureType") String featureType) {
        GmsPromptCurrentResponse response = gmsPromptService.getCurrentPrompt(adminPrincipal, featureType);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CURRENT_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{promptId}")
    @Operation(summary = "GMS prompt detail", description = "Retrieves one saved GMS prompt.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "Prompt ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt detail retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> getPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId) {
        GmsPromptResponse response = gmsPromptService.getPrompt(adminPrincipal, promptId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }

    @PatchMapping("/{promptId}")
    @Operation(summary = "GMS prompt update", description = "Updates one saved GMS prompt.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "Prompt ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicated prompt name", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> updatePrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        @Valid @RequestBody GmsPromptUpdateRequest request, HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.updatePrompt(adminPrincipal, promptId, request,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(UPDATE_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{promptId}")
    @Operation(summary = "GMS prompt delete", description = "Soft-deletes one saved GMS prompt.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "Prompt ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<Void>> deletePrompt(@AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @PathVariable("promptId") Long promptId, HttpServletRequest servletRequest) {
        gmsPromptService.deletePrompt(adminPrincipal, promptId, adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, null));
    }

    @PostMapping("/{promptId}/activate")
    @Operation(summary = "GMS prompt activate", description = "Activates one saved prompt for its feature type.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "Prompt ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt activated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE)))})
    public ResponseEntity<ApiResponse<GmsPromptResponse>> activatePrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        HttpServletRequest servletRequest) {
        GmsPromptResponse response = gmsPromptService.activatePrompt(adminPrincipal, promptId,
            adminClientInfoResolver.resolve(servletRequest));

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ACTIVATE_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{promptId}/test")
    @Operation(summary = "Saved GMS prompt test", description = "Tests a saved prompt with sample input.")
    @Parameter(name = "promptId", in = ParameterIn.PATH, required = true, description = "Prompt ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GMS prompt test created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BAD_REQUEST))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GMS prompt not found", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = PROMPT_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GMS prompt test failed", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<GmsPromptPreviewResponse>> testPrompt(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal, @PathVariable("promptId") Long promptId,
        @Valid @RequestBody GmsPromptTestRequest request) {
        GmsPromptPreviewResponse response = gmsPromptService.testPrompt(adminPrincipal, promptId, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(TEST_SUCCESS_MESSAGE, response));
    }
}
