package com.nemonicworld.community.entity;

/**
 * 커뮤니티 메모 숨김 사유입니다.
 */
public enum CommunityMemoHiddenReason {

    REPORT_THRESHOLD("report_threshold"), AI_MODERATION("ai_moderation"), ADMIN_HIDDEN("admin_hidden");

    private final String value;

    CommunityMemoHiddenReason(String value) {
        this.value = value;
    }

    /**
     * DB enum 컬럼에 저장할 숨김 사유 문자열을 반환합니다.
     */
    public String value() {
        return value;
    }
}
