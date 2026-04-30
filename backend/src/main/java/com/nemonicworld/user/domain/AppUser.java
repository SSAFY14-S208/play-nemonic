package com.nemonicworld.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    public static final String ANONYMOUS_NICKNAME = "익명";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nickname", nullable = false, length = 10)
    private String nickname;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "birthtime")
    private LocalTime birthtime;

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    private AppUser(UUID id, String nickname, String userAgent, LocalDateTime createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.lastSeenAt = createdAt;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static AppUser createAnonymous(UUID id, String userAgent, LocalDateTime createdAt) {
        return new AppUser(id, ANONYMOUS_NICKNAME, userAgent, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public LocalTime getBirthtime() {
        return birthtime;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
