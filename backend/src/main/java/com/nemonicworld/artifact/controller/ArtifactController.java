package com.nemonicworld.artifact.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.artifact.dto.response.ArtifactImageUrlResponse;
import com.nemonicworld.artifact.service.ArtifactService;
import com.nemonicworld.artifact.service.download.ArtifactDownloadFile;
import com.nemonicworld.artifact.service.download.ArtifactDownloadService;
import com.nemonicworld.artifact.service.share.ArtifactShareService;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/artifacts")
@Tag(name = OpenApiTags.ARTIFACT, description = OpenApiTags.ARTIFACT_DESCRIPTION)
public class ArtifactController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ARTIFACT_IMAGE_URL_FOUND_MESSAGE = "산출물 이미지 URL 조회 성공";
    private static final String ARTIFACT_SHARE_CREATED_MESSAGE = "산출물 공유 정보 생성 성공";

    private final ArtifactService artifactService;
    private final ArtifactDownloadService artifactDownloadService;
    private final ArtifactShareService artifactShareService;

    public ArtifactController(ArtifactService artifactService, ArtifactDownloadService artifactDownloadService,
        ArtifactShareService artifactShareService) {
        this.artifactService = artifactService;
        this.artifactDownloadService = artifactDownloadService;
        this.artifactShareService = artifactShareService;
    }

    /**
     * artifact ID로 공통 썸네일과 산출물 종류별 콘텐츠 URL을 조회합니다.
     */
    @GetMapping("/{artifactId}/image-urls")
    @Operation(summary = "산출물 이미지 URL 조회", description = "산출물 테이블 기반으로 썸네일과 원본/GIF 등 콘텐츠 URL 목록을 조회합니다.")
    @Parameter(name = "artifactId", in = ParameterIn.PATH, required = true, description = "산출물 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "산출물 이미지 URL 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "산출물 ID 형식 오류", value = OpenApiErrorExamples.INVALID_ARTIFACT_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 산출물", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "산출물 이미지 없음", value = OpenApiErrorExamples.ARTIFACT_IMAGE_URL_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<ArtifactImageUrlResponse>> getArtifactImageUrls(
        @PathVariable("artifactId") String artifactId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        ArtifactImageUrlResponse response = artifactService.getArtifactImageUrls(userUuid, artifactId);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ARTIFACT_IMAGE_URL_FOUND_MESSAGE, response));
    }

    /**
     * artifact ID로 QR이 합성된 공유용 파일을 생성 또는 재사용해 다운로드합니다.
     */
    @GetMapping("/{artifactId}/download")
    @Operation(summary = "QR 합성 산출물 다운로드", description = "사용자가 보관 중인 산출물에 공유 QR을 합성한 JPG/GIF 파일을 다운로드합니다.")
    @Parameter(name = "artifactId", in = ParameterIn.PATH, required = true, description = "산출물 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "산출물 다운로드 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "산출물 ID 형식 오류", value = OpenApiErrorExamples.INVALID_ARTIFACT_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 산출물", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "산출물 이미지 없음", value = OpenApiErrorExamples.ARTIFACT_IMAGE_URL_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable("artifactId") String artifactId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        ArtifactDownloadFile file = artifactDownloadService.prepareDownloadFile(userUuid, artifactId);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(file.fileName(), StandardCharsets.UTF_8).build();

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString()).body(file.bytes());
    }

    /**
     * artifact ID로 QR 합성 이미지 URL과 플랫폼별 공유 URL을 생성합니다.
     */
    @PostMapping("/{artifactId}/share")
    @Operation(summary = "산출물 SNS 공유 정보 생성", description = "사용자가 보관 중인 산출물의 QR 합성 이미지 URL과 카카오톡/인스타그램 공유용 UTM URL을 생성합니다.")
    @Parameter(name = "artifactId", in = ParameterIn.PATH, required = true, description = "산출물 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "산출물 공유 정보 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "산출물 ID 형식 오류", value = OpenApiErrorExamples.INVALID_ARTIFACT_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 산출물", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "산출물 이미지 없음", value = OpenApiErrorExamples.ARTIFACT_IMAGE_URL_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<ShareCreateResponse>> createArtifactShare(
        @PathVariable("artifactId") String artifactId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        ShareCreateResponse response = artifactShareService.createArtifactShare(userUuid, artifactId);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ARTIFACT_SHARE_CREATED_MESSAGE, response));
    }
}
