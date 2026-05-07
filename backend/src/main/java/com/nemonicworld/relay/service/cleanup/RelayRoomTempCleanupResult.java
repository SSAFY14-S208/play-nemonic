package com.nemonicworld.relay.service.cleanup;

/**
 * CLOSED 방 하나의 임시 파일 정리 결과입니다.
 */
public record RelayRoomTempCleanupResult(String roomCode, boolean cleaned, int deletedObjectCount) {

    public static RelayRoomTempCleanupResult cleaned(String roomCode, int deletedObjectCount) {
        return new RelayRoomTempCleanupResult(roomCode, true, deletedObjectCount);
    }

    public static RelayRoomTempCleanupResult noOp(String roomCode) {
        return new RelayRoomTempCleanupResult(roomCode, false, 0);
    }
}
