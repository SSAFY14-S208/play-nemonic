package com.nemonicworld.auth.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.nemonicworld.common.exception.BadRequestException;

/**
 * 백오피스 관리자 권한 등급입니다.
 */
public enum AdminRole {

    ADMIN("admin"), SUPER_ADMIN("super_admin");

    private final String value;

    AdminRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static AdminRole fromValue(String value) {
        for (AdminRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }

        throw new BadRequestException("지원하지 않는 관리자 권한입니다.");
    }
}
