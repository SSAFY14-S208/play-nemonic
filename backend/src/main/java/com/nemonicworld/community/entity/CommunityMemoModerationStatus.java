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

    public String value() {
        return value;
    }
}
