package com.nemonicworld.community.entity;

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
}
