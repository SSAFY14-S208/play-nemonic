package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlipbookRoomPolicy {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = FlipbookRoomTimeLimitSettings.DEFAULT_TIME_LIMIT_SECONDS;
    public static final int MIN_PARTICIPANTS = FlipbookRoomParticipantLimit.DEFAULT_MIN_PARTICIPANTS;
    public static final int MAX_PARTICIPANTS = FlipbookRoomParticipantLimit.DEFAULT_MAX_PARTICIPANTS;
    public static final int MIN_FRAMES_PER_FLIPBOOK = defaultMinFramesPerFlipbook();
    public static final int HOST_JOIN_ORDER = 0;
    public static final int ROOM_UPDATE_MAX_RETRIES = 3;
    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = defaultReconnectGraceSeconds();
    public static final String ROOM_UPDATE_CONFLICT_MESSAGE = "동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요.";
    public static final String ROOM_CONNECTION_UPDATE_CONFLICT_MESSAGE = "동시 접속 상태 변경 요청이 많아 플립북 방 연결 상태를 "
        + "갱신하지 못했습니다. 다시 시도해주세요.";
    public static final String ROOM_KICK_UPDATE_CONFLICT_MESSAGE = "동시 강퇴 요청이 많아 플립북 방 강퇴 상태를 갱신하지 못했습니다. "
        + "다시 시도해주세요.";
    public static final String ROOM_START_UPDATE_CONFLICT_MESSAGE = "동시 게임 시작 요청이 많아 플립북 방 시작 상태를 갱신하지 못했습니다. "
        + "다시 시도해주세요.";
    public static final String ROOM_TIMEOUT_UPDATE_CONFLICT_MESSAGE = "동시 타임아웃 처리 요청이 많아 플립북 프레임 자동 제출 상태를 "
        + "갱신하지 못했습니다. 다시 시도해주세요.";

    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomValidationSupport validationSupport;
    private final FlipbookRoomParticipantPolicySupport participantPolicySupport;
    private final FlipbookRoomActionPolicySupport actionPolicySupport;
    private final FlipbookRoomReconnectPolicySupport reconnectPolicySupport;

    @Autowired
    public FlipbookRoomPolicy(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomValidationSupport validationSupport, FlipbookRoomParticipantPolicySupport participantPolicySupport,
        FlipbookRoomActionPolicySupport actionPolicySupport,
        FlipbookRoomReconnectPolicySupport reconnectPolicySupport) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.validationSupport = validationSupport;
        this.participantPolicySupport = participantPolicySupport;
        this.actionPolicySupport = actionPolicySupport;
        this.reconnectPolicySupport = reconnectPolicySupport;
    }

    public FlipbookRoomPolicy(RoomCodeGenerator roomCodeGenerator, FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider) {
        this(flipbookRoomRepository,
            new FlipbookRoomValidationSupport(roomCodeGenerator, flipbookRuntimeSettingsProvider),
            new FlipbookRoomParticipantPolicySupport(), new FlipbookRoomActionPolicySupport(),
            new FlipbookRoomReconnectPolicySupport(flipbookRuntimeSettingsProvider,
                new FlipbookRoomParticipantPolicySupport()));
    }

    private static long defaultReconnectGraceSeconds() {
        return FlipbookReconnectGraceSettings.DEFAULT_RECONNECT_GRACE_SECONDS;
    }

    private static int defaultMinFramesPerFlipbook() {
        return FlipbookMinFramesPerFlipbookSettings.DEFAULT_MIN_FRAMES_PER_FLIPBOOK;
    }

    public void validateNicknameRegistered(AppUser appUser) {
        validationSupport.validateNicknameRegistered(appUser);
    }

    public void validateRoomCode(String roomCodeValue) {
        validationSupport.validateRoomCode(roomCodeValue);
    }

    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request) {
        return validationSupport.resolveTimeLimitSeconds(request);
    }

    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request, FlipbookRoomTimeLimitSettings settings) {
        return validationSupport.resolveTimeLimitSeconds(request, settings);
    }

    public FlipbookRoomState findRoomState(String roomCodeValue) {
        return flipbookRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    public Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return participantPolicySupport.findParticipant(roomState, userUuid);
    }

    public FlipbookRoomParticipant requireParticipant(FlipbookRoomState roomState, String userUuid) {
        return participantPolicySupport.requireParticipant(roomState, userUuid);
    }

    public FlipbookRoomParticipant requireConnectionParticipant(FlipbookRoomState roomState, String userUuid) {
        return participantPolicySupport.requireConnectionParticipant(roomState, userUuid);
    }

    public FlipbookRoomParticipant requireKickTargetParticipant(FlipbookRoomState roomState, String targetUserUuid) {
        return participantPolicySupport.requireKickTargetParticipant(roomState, targetUserUuid);
    }

    public void validateRoomHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        actionPolicySupport.validateRoomHost(viewerUserUuid, roomState, participant);
    }

    public void validateRoomCloseHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        actionPolicySupport.validateRoomCloseHost(viewerUserUuid, roomState, participant);
    }

    public void validateKickHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        actionPolicySupport.validateKickHost(viewerUserUuid, roomState, participant);
    }

    public void validateWaitingRoomForSettings(FlipbookRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForSettings(roomState);
    }

    public void validateWaitingRoomForKick(FlipbookRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForKick(roomState);
    }

    public void validateWaitingRoomForLeave(FlipbookRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForLeave(roomState);
    }

    public void validateManualClosableRoom(FlipbookRoomState roomState) {
        actionPolicySupport.validateManualClosableRoom(roomState);
    }

    public void validateStartableRoomStatus(FlipbookRoomState roomState) {
        actionPolicySupport.validateStartableRoomStatus(roomState);
    }

    public void validateAssignmentQueryableRoom(FlipbookRoomState roomState) {
        actionPolicySupport.validateAssignmentQueryableRoom(roomState);
    }

    public FlipbookFrameAssignment requireCurrentAssignment(FlipbookRoomState roomState, String viewerUserUuid) {
        return actionPolicySupport.requireCurrentAssignment(roomState, viewerUserUuid);
    }

    public Optional<FlipbookFrameAssignment> findFrameAssignment(FlipbookRoomState roomState, int flipbookIndex,
        int frameIndex) {
        return actionPolicySupport.findFrameAssignment(roomState, flipbookIndex, frameIndex);
    }

    public List<FlipbookRoomParticipant> findStartParticipants(FlipbookRoomState roomState) {
        return participantPolicySupport.findStartParticipants(roomState);
    }

    public int resolveDefaultTotalRounds() {
        return validationSupport.resolveDefaultTotalRounds();
    }

    public int resolveDefaultTotalRounds(int minFramesPerFlipbook) {
        return validationSupport.resolveDefaultTotalRounds(minFramesPerFlipbook);
    }

    public void validateKickTarget(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant targetParticipant) {
        actionPolicySupport.validateKickTarget(viewerUserUuid, roomState, targetParticipant);
    }

    public void validateWebSocketConnectableRoom(FlipbookRoomState roomState) {
        reconnectPolicySupport.validateWebSocketConnectableRoom(roomState);
    }

    public void validateNotKicked(FlipbookRoomState roomState, String userUuid) {
        participantPolicySupport.validateNotKicked(roomState, userUuid);
    }

    public void validateNotDropped(FlipbookRoomState roomState, String userUuid) {
        participantPolicySupport.validateNotDropped(roomState, userUuid);
    }

    public boolean isKicked(FlipbookRoomState roomState, String userUuid) {
        return participantPolicySupport.isKicked(roomState, userUuid);
    }

    public boolean isDropped(FlipbookRoomState roomState, String userUuid) {
        return participantPolicySupport.isDropped(roomState, userUuid);
    }

    public boolean requiresReconnectGrace(FlipbookRoomState roomState) {
        return reconnectPolicySupport.requiresReconnectGrace(roomState);
    }

    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now) {
        return reconnectPolicySupport.canReconnect(participant, now);
    }

    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now, Duration reconnectGracePeriod) {
        return reconnectPolicySupport.canReconnect(participant, now, reconnectGracePeriod);
    }

    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now) {
        reconnectPolicySupport.requireReconnectable(participant, now);
    }

    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now,
        Duration reconnectGracePeriod) {
        reconnectPolicySupport.requireReconnectable(participant, now, reconnectGracePeriod);
    }

    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now) {
        reconnectPolicySupport.validateExistingParticipantReturn(roomState, participant, now);
    }

    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now, Duration reconnectGracePeriod) {
        reconnectPolicySupport.validateExistingParticipantReturn(roomState, participant, now, reconnectGracePeriod);
    }

    FlipbookRoomViewerBlockedReason findJoinBlockedReason(FlipbookRoomState roomState, String viewerUserUuid) {
        return reconnectPolicySupport.findJoinBlockedReason(roomState, viewerUserUuid);
    }

    boolean canStart(FlipbookRoomState roomState, boolean host) {
        return actionPolicySupport.canStart(roomState, host);
    }
}
