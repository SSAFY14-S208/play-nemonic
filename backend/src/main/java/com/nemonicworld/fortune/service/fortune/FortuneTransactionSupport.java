package com.nemonicworld.fortune.service.fortune;

import com.nemonicworld.fortune.repository.FortuneCreateCommand;
import com.nemonicworld.fortune.repository.FortuneDetailRow;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FortuneTransactionSupport {

    private final FortuneRepository fortuneRepository;

    public FortuneTransactionSupport(FortuneRepository fortuneRepository) {
        this.fortuneRepository = fortuneRepository;
    }

    @Transactional(readOnly = true)
    public Optional<FortuneTodayRow> findTodayFortune(UUID userId, LocalDate today) {
        return fortuneRepository.findTodayFortune(userId, today);
    }

    @Transactional(readOnly = true)
    public Optional<FortuneDetailRow> findTodayFortuneDetail(UUID userId, LocalDate today) {
        return fortuneRepository.findTodayFortuneDetail(userId, today);
    }

    @Transactional
    public void saveFortune(FortuneCreateCommand command) {
        fortuneRepository.saveFortune(command);
    }

    @Transactional
    public void updateFortuneImageObjectKey(UUID fortuneId, String imageObjectKey, LocalDateTime updatedAt) {
        fortuneRepository.updateFortuneImageObjectKey(fortuneId, imageObjectKey, updatedAt);
    }
}
