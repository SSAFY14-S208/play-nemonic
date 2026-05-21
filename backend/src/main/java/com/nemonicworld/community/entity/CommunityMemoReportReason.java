package com.nemonicworld.community.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 커뮤니티 메모 신고 사유입니다.
 */
public enum CommunityMemoReportReason {

    INAPPROPRIATE("inappropriate", "부적절한 콘텐츠"), ABUSE_HATE("abuse_hate", "욕설/비방/혐오"), SEXUAL_CONTENT("sexual_content",
        "선정적/음란물"), VIOLENCE_THREAT("violence_threat", "폭력적/위협적 표현"), SPAM("spam", "스팸/광고"), PERSONAL_INFO(
            "personal_info", "개인정보 노출"), IMPERSONATION("impersonation", "도용/사칭"), OTHER("other", "기타");

    private final String value;
    private final String displayName;

    CommunityMemoReportReason(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    /**
     * DB enum 컬럼에 저장할 신고 사유 문자열을 반환합니다.
     */
    public String value() {
        return value;
    }

    /**
     * API 요청의 한글 신고 사유와 정확히 일치하는 항목을 찾습니다.
     */
    public static Optional<CommunityMemoReportReason> findByDisplayName(String displayName) {
        return Arrays.stream(values()).filter(reason -> reason.displayName.equals(displayName)).findFirst();
    }

    /**
     * DB enum 값과 정확히 일치하는 신고 사유를 찾습니다.
     */
    public static Optional<CommunityMemoReportReason> findByValue(String value) {
        return Arrays.stream(values()).filter(reason -> reason.value.equals(value)).findFirst();
    }
}
