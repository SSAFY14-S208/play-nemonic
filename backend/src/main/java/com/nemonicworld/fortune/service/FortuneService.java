package com.nemonicworld.fortune.service;

import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;

public interface FortuneService {

    FortuneAvailabilityResponse getTodayAvailability(String userUuidValue);
}
