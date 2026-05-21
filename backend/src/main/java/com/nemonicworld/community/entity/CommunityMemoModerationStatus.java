package com.nemonicworld.community.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 커뮤니티 메모 게시 전 검수 상태입니다.
 */
public enum CommunityMemoModerationStatus {

    PENDING("pending"), ALLOWED("allowed"), BLOCKED("blocked");

    private final String value;

    CommunityMemoModerationStatus(String value) {
        this.value = value;
    }

    /**
     * DB enum 컬럼에 저장할 모더레이션 상태 문자열을 반환합니다.
     */
    public String value() {
        return value;
    }

    /**
     * 관리자 조회 필터 문자열과 일치하는 모더레이션 상태를 찾습니다.
     */
    public static Optional<CommunityMemoModerationStatus> findByValue(String value) {
        return Arrays.stream(values()).filter(status -> status.value.equals(value)).findFirst();
    }
}
