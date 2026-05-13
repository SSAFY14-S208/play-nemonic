package com.nemonicworld.community.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 커뮤니티 메모가 어떤 출처에서 게시되었는지 나타내는 API/DB 공통 값입니다.
 */
public enum CommunityMemoSourceType {

    DIRECT("DIRECT"), GALLERY("GALLERY");

    private final String value;

    CommunityMemoSourceType(String value) {
        this.value = value;
    }

    /**
     * API/DB에서 사용하는 sourceType 문자열 값을 반환합니다.
     */
    public String value() {
        return value;
    }

    /**
     * 요청 문자열과 일치하는 커뮤니티 메모 출처 타입을 찾습니다.
     */
    public static Optional<CommunityMemoSourceType> findByValue(String value) {
        return Arrays.stream(values()).filter(sourceType -> sourceType.value.equals(value)).findFirst();
    }
}
