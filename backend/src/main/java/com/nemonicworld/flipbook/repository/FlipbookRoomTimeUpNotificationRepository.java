package com.nemonicworld.flipbook.repository;

import java.time.Duration;
import java.time.LocalDateTime;

public interface FlipbookRoomTimeUpNotificationRepository {

    boolean markRoundTimeUpNotified(String roomCode, int round, LocalDateTime roundDeadlineAt, Duration ttl);
}
