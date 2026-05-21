package com.nemonicworld.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커뮤니티 메모 신고 요청입니다.
 */
@Schema(description = "커뮤니티 메모 신고 요청")
public record CommunityMemoReportRequest(String reason, String reasonDetail) {

    @Override
    @Schema(description = "신고 사유", example = "욕설/비방/혐오", allowableValues = {"부적절한 콘텐츠", "욕설/비방/혐오", "선정적/음란물",
        "폭력적/위협적 표현", "스팸/광고", "개인정보 노출", "도용/사칭", "기타"})
    public String reason() {
        return reason;
    }

    @Override
    @Schema(description = "신고 상세 사유", nullable = true, example = "욕설이 포함되어 있어요.")
    public String reasonDetail() {
        return reasonDetail;
    }
}
