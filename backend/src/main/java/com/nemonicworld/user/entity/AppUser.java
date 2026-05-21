package com.nemonicworld.user.entity;

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
/**
 * 서비스에서 익명 사용자를 식별하기 위해 사용하는 사용자 엔티티입니다.
 *
 * <p>
 * 기본 식별자는 서버가 발급한 UUID이며, User-Agent는 식별자가 아닌 참고용 메타데이터입니다.
 */
public class AppUser {

    // 최초 익명 사용자에게 저장되는 기본 닉네임입니다.
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

    @Column(name = "is_lunar")
    private Boolean isLunar;

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // JPA가 엔티티를 복원할 때 사용하는 기본 생성자입니다.
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

    /**
     * 익명 사용자 생성 정책을 한곳에 모아 엔티티 상태가 일관되게 저장되도록 합니다.
     */
    public static AppUser createAnonymous(UUID id, String userAgent, LocalDateTime createdAt) {
        return new AppUser(id, ANONYMOUS_NICKNAME, userAgent, createdAt);
    }

    /**
     * 재방문한 익명 사용자의 최근 방문 정보를 갱신합니다.
     */
    public void updateLastSeen(String userAgent, LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
        this.userAgent = userAgent;
        this.updatedAt = lastSeenAt;
    }

    /**
     * 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    public void updateNickname(String nickname, LocalDateTime updatedAt) {
        this.nickname = nickname;
        this.updatedAt = updatedAt;
    }

    /**
     * 운세 기능에서 재사용할 생년월일 정보가 모두 등록되어 있는지 확인합니다.
     */
    public boolean hasBirthInfo() {
        return birthday != null && birthtime != null && isLunar != null;
    }

    /**
     * 생년월일, 생시, 양력/음력 여부를 함께 갱신해 부분 저장 상태를 만들지 않도록 합니다.
     */
    public void updateBirthInfo(LocalDate birthday, LocalTime birthtime, Boolean isLunar, LocalDateTime updatedAt) {
        this.birthday = birthday;
        this.birthtime = birthtime;
        this.isLunar = isLunar;
        this.updatedAt = updatedAt;
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

    public Boolean getIsLunar() {
        return isLunar;
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
