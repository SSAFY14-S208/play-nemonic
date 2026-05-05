package com.nemonicworld.auth.entity;

import java.time.LocalDateTime;

/**
 * admin_user 테이블의 관리자 계정 정보를 표현합니다.
 */
public class AdminUser {

    private final Long id;
    private final String loginId;
    private final String passwordHash;
    private final String nickname;
    private final String email;
    private final AdminRole role;
    private final LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;

    public AdminUser(Long id, String loginId, String passwordHash, String nickname, String email, AdminRole role,
        LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.email = email;
        this.role = role;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public AdminRole getRole() {
        return role;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
