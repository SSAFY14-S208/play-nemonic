package com.nemonicworld.fortune.service.fortune;

import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FortuneAvailabilityUseCase {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final FortuneRepository fortuneRepository;
    private final AnonymousUserResolver anonymousUserResolver;

    public FortuneAvailabilityUseCase(FortuneRepository fortuneRepository,
        AnonymousUserResolver anonymousUserResolver) {
        this.fortuneRepository = fortuneRepository;
        this.anonymousUserResolver = anonymousUserResolver;
    }

    @Transactional(readOnly = true)
    public FortuneAvailabilityResponse getTodayAvailability(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        OffsetDateTime nextAvailableAt = today.plusDays(1).atStartOfDay(KST_ZONE).toOffsetDateTime();
        Optional<FortuneTodayRow> todayFortune = fortuneRepository.findTodayFortune(user.getId(), today);
        FortuneAvailabilityResponse response;

        if (todayFortune.isEmpty()) {
            response = new FortuneAvailabilityResponse(true, today, null, null, nextAvailableAt);
        } else {
            FortuneTodayRow row = todayFortune.get();
            response = new FortuneAvailabilityResponse(false, row.fortuneDate(), row.fortuneId().toString(),
                row.createdAt(), nextAvailableAt);
        }

        logAvailabilityChecked(user.getId(), response);

        return response;
    }

    private void logAvailabilityChecked(UUID userUuid, FortuneAvailabilityResponse response) {
        FortuneEventLogger.apiBusiness("fortune_availability_checked", userUuid,
            FortuneEventLogger.metadata("fortune_date", response.fortuneDate(), "available", response.available(),
                "today_fortune_id", response.todayFortuneId(), "result", "success"));
    }
}
