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

    public String value() {
        return value;
    }

    public static Optional<CommunityMemoSourceType> findByValue(String value) {
        return Arrays.stream(values()).filter(sourceType -> sourceType.value.equals(value)).findFirst();
    }
}
