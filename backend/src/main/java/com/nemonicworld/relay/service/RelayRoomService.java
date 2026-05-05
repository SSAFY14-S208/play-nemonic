package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomMyAssignmentResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;

/**
 * 릴레이 방 유스케이스를 정의합니다.
 */
public interface RelayRoomService {

    /**
     * 기존 익명 사용자를 방장으로 하는 새 릴레이 방을 생성합니다.
     */
    RelayRoomCreateResponse createRoom(String userUuidValue);

    /**
     * 기존 익명 사용자 기준으로 현재 릴레이 방 상태를 조회합니다.
     */
    RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue);

    /**
     * 기존 익명 사용자를 릴레이 방에 새로 입장시키거나 기존 참여자의 재접속 복귀를 처리합니다.
     */
    RelayRoomStateResponse joinRoom(String userUuidValue, String roomCodeValue);

    /**
     * 기존 익명 사용자인 방장이 대기 중 릴레이 방의 설정을 변경합니다.
     */
    RelayRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        RelayRoomSettingsRequest request);

    /**
     * 기존 익명 사용자인 방장이 대기 중인 릴레이 방을 게임 진행 상태로 전환합니다.
     */
    RelayRoomStateResponse startRoom(String userUuidValue, String roomCodeValue);

    /**
     * 기존 익명 사용자가 진행 중인 릴레이 방에서 현재 그릴 배정을 조회합니다.
     */
    RelayRoomMyAssignmentResponse getMyAssignment(String userUuidValue, String roomCodeValue);

    /**
     * 진행 중인 릴레이 방에서 자신의 순서일 때, 그린 그림을 제출합니다.
     */
    RelayRoomSubmissionResponse submitCurrentPart(String userUuidValue, String roomCodeValue,
        RelayRoomSubmissionRequest request);

    /**
     * WebSocket 연결 성공 시 기존 릴레이 참여자를 연결 상태로 갱신합니다.
     */
    RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue);

    /**
     * WebSocket 연결 해제 시 기존 릴레이 참여자를 연결 해제 상태로 갱신합니다.
     */
    RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue);
}
