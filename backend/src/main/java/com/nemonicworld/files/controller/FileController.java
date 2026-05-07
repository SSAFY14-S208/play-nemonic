package com.nemonicworld.files.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.dto.response.FileViewUrlResponse;
import com.nemonicworld.files.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "파일 API")
public class FileController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String PRESIGN_SUCCESS_MESSAGE = "Presigned URL 발급 성공";
    private static final String VIEW_URL_SUCCESS_MESSAGE = "파일 조회 URL 발급 성공";
    private static final String CONFIRM_SUCCESS_MESSAGE = "파일 업로드 확인 성공";
    private static final String DELETE_SUCCESS_MESSAGE = "파일 삭제 성공";

    private final FileService fileService;

    @PostMapping("/presign")
    @Operation(summary = "이미지 업로드 Presigned URL 발급", description = "MinIO 직접 PUT 업로드 URL을 발급합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presigned URL 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "파일명 오류", value = OpenApiErrorExamples.INVALID_FILE_NAME),
            @ExampleObject(name = "파일 크기 오류", value = OpenApiErrorExamples.INVALID_BYTE_SIZE),
            @ExampleObject(name = "파일 형식 오류", value = OpenApiErrorExamples.UNSUPPORTED_FILE_TYPE),
            @ExampleObject(name = "purpose 오류", value = OpenApiErrorExamples.UNSUPPORTED_PURPOSE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "파일 크기 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_SIZE_EXCEEDED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일 저장소 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_STORAGE_ERROR)))})
    public ResponseEntity<ApiResponse<FilePresignResponse>> presign(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @RequestBody FilePresignRequest request) {
        FilePresignResponse response = fileService.createPresignedUrl(userUuid, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PRESIGN_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{fileId}/view-url")
    @Operation(summary = "파일 조회 Presigned URL 발급", description = "업로드 완료된 private MinIO 객체를 조회하기 위한 GET URL을 발급합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @Parameter(name = "fileId", in = ParameterIn.PATH, required = true, description = "파일 업로드 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 조회 URL 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "fileId 형식 오류", value = OpenApiErrorExamples.INVALID_FILE_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 파일 업로드", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 업로드 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 조회 상태 충돌", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_VIEW_STATUS_CONFLICT))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일 저장소 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_STORAGE_ERROR)))})
    public ResponseEntity<ApiResponse<FileViewUrlResponse>> viewUrl(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("fileId") String fileId) {
        FileViewUrlResponse response = fileService.createViewUrl(userUuid, fileId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(VIEW_URL_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{fileId}/confirm")
    @Operation(summary = "파일 업로드 완료 확인", description = "MinIO에 업로드된 객체를 확인하고 파일 상태를 UPLOADED로 변경합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @Parameter(name = "fileId", in = ParameterIn.PATH, required = true, description = "파일 업로드 ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 업로드 확인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "fileId 형식 오류", value = OpenApiErrorExamples.INVALID_FILE_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 파일 업로드", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 업로드 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 업로드 상태 충돌", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_UPLOAD_STATUS_CONFLICT))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "파일 크기 초과", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_SIZE_EXCEEDED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일 저장소 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_STORAGE_ERROR)))})
    public ResponseEntity<ApiResponse<FileConfirmResponse>> confirm(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("fileId") String fileId) {
        FileConfirmResponse response = fileService.confirmUpload(userUuid, fileId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CONFIRM_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "파일 삭제", description = "pending 파일 업로드를 취소하고 MinIO object를 삭제합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @Parameter(name = "fileId", in = ParameterIn.PATH, required = true, description = "파일 업로드 ID")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "fileId 형식 오류", value = OpenApiErrorExamples.INVALID_FILE_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 파일 업로드", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 업로드 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 업로드 상태 충돌", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_DELETE_STATUS_CONFLICT))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일 저장소 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_STORAGE_ERROR)))})
    public ResponseEntity<ApiResponse<FileDeleteResponse>> delete(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @PathVariable("fileId") String fileId) {
        FileDeleteResponse response = fileService.deleteUpload(userUuid, fileId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, response));
    }

}
