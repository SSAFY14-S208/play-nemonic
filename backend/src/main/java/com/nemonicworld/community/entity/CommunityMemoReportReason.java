package com.nemonicworld.community.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 커뮤니티 메모 신고 사유입니다.
 */
public enum CommunityMemoReportReason {

    INAPPROPRIATE("inappropriate"), SPAM("spam"), OTHER("other");

    private final String value;

    CommunityMemoReportReason(String value) {
        this.value = value;
    }

    /**
     * DB enum 컬럼에 저장할 신고 사유 문자열을 반환합니다.
     */
    public String value() {
        return value;
    }

    /**
     * API 요청 문자열과 정확히 일치하는 신고 사유를 찾습니다.
     */
    public static Optional<CommunityMemoReportReason> findByValue(String value) {
        return Arrays.stream(values()).filter(reason -> reason.value.equals(value)).findFirst();
    }
}
