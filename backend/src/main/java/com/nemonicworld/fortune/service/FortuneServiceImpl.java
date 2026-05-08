package com.nemonicworld.fortune.service;

import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 운세 생성 가능 여부와 생성 정책을 처리하는 서비스입니다.
 */
@Service
public class FortuneServiceImpl implements FortuneService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final FortuneRepository fortuneRepository;
    private final AnonymousUserResolver anonymousUserResolver;

    public FortuneServiceImpl(FortuneRepository fortuneRepository, AnonymousUserResolver anonymousUserResolver) {
        this.fortuneRepository = fortuneRepository;
        this.anonymousUserResolver = anonymousUserResolver;
    }

    /**
     * 기존 사용자를 확인한 뒤 KST 오늘 날짜에 운세가 이미 생성되었는지 조회합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public FortuneAvailabilityResponse getTodayAvailability(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        OffsetDateTime nextAvailableAt = today.plusDays(1).atStartOfDay(KST_ZONE).toOffsetDateTime();
        Optional<FortuneTodayRow> todayFortune = fortuneRepository.findTodayFortune(user.getId(), today);

        if (todayFortune.isEmpty()) {
            return new FortuneAvailabilityResponse(true, today, null, null, nextAvailableAt);
        }

        FortuneTodayRow row = todayFortune.get();
        return new FortuneAvailabilityResponse(false, row.fortuneDate(), row.fortuneId().toString(), row.createdAt(),
            nextAvailableAt);
    }
}
