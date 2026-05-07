package com.nemonicworld.community.entity;

/**
 * 커뮤니티 메모 soft delete 사유입니다.
 */
public enum CommunityMemoDeletedReason {

    EXPIRED("expired"), ADMIN_REMOVED("admin_removed"), USER_DELETE("user_delete");

    private final String value;

    CommunityMemoDeletedReason(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
