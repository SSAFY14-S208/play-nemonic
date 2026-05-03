package com.nemonicworld.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnonymousUserResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnonymousUserResolver anonymousUserResolver;

    @Test
    void resolveReturnsExistingAnonymousUser() {
        UUID userUuid = UUID.randomUUID();
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", LocalDateTime.now());
        given(userRepository.findById(userUuid)).willReturn(Optional.of(appUser));

        AppUser resolvedUser = anonymousUserResolver.resolve(userUuid.toString());

        assertThat(resolvedUser).isSameAs(appUser);
    }

    @Test
    void parseUuidRejectsNullOrBlankUuid() {
        assertThatThrownBy(() -> anonymousUserResolver.parseUuid(null)).isInstanceOf(BadRequestException.class)
            .hasMessage("유효하지 않은 UUID 형식입니다.");
        assertThatThrownBy(() -> anonymousUserResolver.parseUuid(" ")).isInstanceOf(BadRequestException.class)
            .hasMessage("유효하지 않은 UUID 형식입니다.");
    }

    @Test
    void parseUuidRejectsInvalidUuidFormat() {
        assertThatThrownBy(() -> anonymousUserResolver.parseUuid("not-a-uuid")).isInstanceOf(BadRequestException.class)
            .hasMessage("유효하지 않은 UUID 형식입니다.");
    }

    @Test
    void resolveRejectsMissingAnonymousUser() {
        UUID missingUserUuid = UUID.randomUUID();
        given(userRepository.findById(missingUserUuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> anonymousUserResolver.resolve(missingUserUuid.toString()))
            .isInstanceOf(NotFoundException.class).hasMessage("존재하지 않는 사용자입니다.");
    }
}
