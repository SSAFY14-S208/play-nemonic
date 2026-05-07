package com.nemonicworld.share.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SNS 공유 정보 생성 응답")
public record ShareCreateResponse(
    @Schema(description = "로그 추적용 공유 토큰", example = "q83Nc7Zk9xR0pLm2TavY3A") String shareToken,
    @Schema(description = "공유할 산출물 이미지 URL", example = "https://minio.example.com/result.png") String imageUrl,
    @Schema(description = "네모닉 사이트 기본 URL", example = "https://nemonicworld.com") String siteUrl,
    @Schema(description = "카카오톡 공유용 UTM URL") String kakaoUrl,
    @Schema(description = "인스타그램 공유용 UTM URL") String instagramUrl) {
}
