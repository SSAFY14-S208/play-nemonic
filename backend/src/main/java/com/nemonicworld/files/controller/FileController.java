package com.nemonicworld.files.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final String USER_UUID_HEADER = "X-User-UUID";
    private static final String PRESIGN_SUCCESS_MESSAGE = "Presigned URL 발급 성공";
    private static final String CONFIRM_SUCCESS_MESSAGE = "파일 업로드 확인 성공";
    private static final String DELETE_SUCCESS_MESSAGE = "파일 삭제 성공";

    private final FileService fileService;

    @PostMapping("/presign")
    @Operation(summary = "이미지 업로드 Presigned URL 발급", description = "MinIO 직접 PUT 업로드 URL을 발급합니다.")
    @Parameter(name = USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presigned URL 발급 성공")
    public ResponseEntity<ApiResponse<FilePresignResponse>> presign(
        @RequestHeader(value = USER_UUID_HEADER, required = false) String userUuid,
        @Valid @RequestBody FilePresignRequest request) {
        FilePresignResponse response = fileService.createPresignedUrl(userUuid, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PRESIGN_SUCCESS_MESSAGE, response));
    }

    @PostMapping("/{fileId}/confirm")
    @Operation(summary = "파일 업로드 완료 확인", description = "MinIO에 업로드된 객체를 확인하고 파일 상태를 UPLOADED로 변경합니다.")
    @Parameter(name = USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 업로드 확인 성공")
    public ResponseEntity<ApiResponse<FileConfirmResponse>> confirm(
        @RequestHeader(value = USER_UUID_HEADER, required = false) String userUuid, @PathVariable String fileId) {
        FileConfirmResponse response = fileService.confirmUpload(userUuid, fileId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(CONFIRM_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "파일 삭제", description = "pending 파일 업로드를 취소하고 MinIO object를 삭제합니다.")
    @Parameter(name = USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "파일 삭제 성공")
    public ResponseEntity<ApiResponse<FileDeleteResponse>> delete(
        @RequestHeader(value = USER_UUID_HEADER, required = false) String userUuid, @PathVariable String fileId) {
        FileDeleteResponse response = fileService.deleteUpload(userUuid, fileId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(DELETE_SUCCESS_MESSAGE, response));
    }

}
