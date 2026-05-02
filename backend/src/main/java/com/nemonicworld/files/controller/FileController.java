package com.nemonicworld.files.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.files.dto.request.FilePresignRequest;
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
}
