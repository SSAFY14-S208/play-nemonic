package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플립북 현재 배정 힌트 응답")
public record FlipbookFrameHintResponse(@Schema(description = "힌트로 제공되는 플립북 번호", example = "1") int flipbookIndex,
    @Schema(description = "힌트로 제공되는 프레임 번호", example = "0") int frameIndex,
    @Schema(description = "힌트로 제공되는 라운드", example = "1") int round,
    @Schema(description = "힌트 이미지 객체 키", example = "flipbook/tmp/AB3K9Q/1/0.png") String objectKey,
    @Schema(description = "힌트 이미지 URL", nullable = true) String url,
    @Schema(description = "빈 제출 여부", example = "false") boolean empty) {

    public static FlipbookFrameHintResponse from(FlipbookFrameAssignment assignment, String url) {
        return new FlipbookFrameHintResponse(assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(),
            assignment.objectKey(), url, assignment.empty());
    }
}
