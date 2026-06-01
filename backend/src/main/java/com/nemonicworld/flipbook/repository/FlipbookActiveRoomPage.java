package com.nemonicworld.flipbook.repository;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.util.List;

public record FlipbookActiveRoomPage(List<FlipbookRoomState> items, long totalElements) {
}
