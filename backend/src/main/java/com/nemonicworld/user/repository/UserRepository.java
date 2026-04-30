package com.nemonicworld.user.repository;

import com.nemonicworld.user.entity.AppUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * app_user 테이블에 대한 Spring Data JPA 저장소입니다.
 */
public interface UserRepository extends JpaRepository<AppUser, UUID> {
}
