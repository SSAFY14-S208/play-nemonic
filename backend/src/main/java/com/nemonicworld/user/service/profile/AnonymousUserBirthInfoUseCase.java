package com.nemonicworld.user.service.profile;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import com.nemonicworld.user.dto.response.AnonymousUserBirthInfoResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import com.nemonicworld.user.service.support.AnonymousUserBirthInfoSupport;
import com.nemonicworld.user.service.support.AnonymousUserBirthInfoSupport.BirthInfo;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymousUserBirthInfoUseCase {

    private static final String BIRTH_INFO_ALREADY_REGISTERED_MESSAGE = "이미 생년월일 정보가 등록되어 있습니다.";
    private static final String BIRTH_INFO_NOT_REGISTERED_MESSAGE = "등록된 생년월일 정보가 없습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final AnonymousUserBirthInfoSupport anonymousUserBirthInfoSupport;

    public AnonymousUserBirthInfoUseCase(AnonymousUserResolver anonymousUserResolver,
        AnonymousUserBirthInfoSupport anonymousUserBirthInfoSupport) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.anonymousUserBirthInfoSupport = anonymousUserBirthInfoSupport;
    }

    @Transactional
    public AnonymousUserBirthInfoResponse registerAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        BirthInfo birthInfo = anonymousUserBirthInfoSupport.parseBirthInfo(request);
        AppUser appUser = anonymousUserResolver.resolve(userUuid);

        if (appUser.hasBirthInfo()) {
            throw new ConflictException(BIRTH_INFO_ALREADY_REGISTERED_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        appUser.updateBirthInfo(birthInfo.birthday(), birthInfo.birthtime(), birthInfo.isLunar(), now);
        StructuredEventLogger.apiBusiness("birth_info_saved", "user", appUser.getId().toString(), StructuredEventLogger
            .metadata("operation", "create", "is_lunar", birthInfo.isLunar(), "result", "success"));

        return toBirthInfoResponse(appUser);
    }

    @Transactional
    public AnonymousUserBirthInfoResponse updateAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        BirthInfo birthInfo = anonymousUserBirthInfoSupport.parseBirthInfo(request);
        AppUser appUser = anonymousUserResolver.resolve(userUuid);

        if (!appUser.hasBirthInfo()) {
            throw new NotFoundException(BIRTH_INFO_NOT_REGISTERED_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        appUser.updateBirthInfo(birthInfo.birthday(), birthInfo.birthtime(), birthInfo.isLunar(), now);
        StructuredEventLogger.apiBusiness("birth_info_saved", "user", appUser.getId().toString(), StructuredEventLogger
            .metadata("operation", "update", "is_lunar", birthInfo.isLunar(), "result", "success"));

        return toBirthInfoResponse(appUser);
    }

    private AnonymousUserBirthInfoResponse toBirthInfoResponse(AppUser appUser) {
        return new AnonymousUserBirthInfoResponse(appUser.getId().toString(), appUser.getBirthday(),
            appUser.getBirthtime(), appUser.getIsLunar(), appUser.getUpdatedAt());
    }
}
