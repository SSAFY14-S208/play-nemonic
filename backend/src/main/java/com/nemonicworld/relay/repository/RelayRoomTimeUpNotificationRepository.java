package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.time.Duration;
import java.time.LocalDateTime;

public interface RelayRoomTimeUpNotificationRepository {

    boolean markPartTimeUpNotified(String roomCode, RelayDrawingPart part, LocalDateTime partDeadlineAt, Duration ttl);
}
