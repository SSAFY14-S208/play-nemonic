package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.redis.RelayRoomState;
import java.util.List;

public record RelayActiveRoomPage(List<RelayRoomState> items, long totalElements) {
}
