package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;

/**
 * 플립북 방 유스케이스를 정의합니다.
 */
public interface FlipbookRoomService {

    /**
     * 기존 익명 사용자를 방장으로 하는 새 플립북 방을 생성합니다.
     */
    FlipbookRoomCreateResponse createRoom(String userUuidValue);
}
