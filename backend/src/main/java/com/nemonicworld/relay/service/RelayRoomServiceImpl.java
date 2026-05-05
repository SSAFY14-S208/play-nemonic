package com.nemonicworld.relay.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 릴레이 방 생성, 상태 조회, 입장/복귀 유스케이스를 처리하는 서비스 구현체입니다.
 *
 * 사용자 UUID 검증은 기존 app_user를 읽어서 수행하고, 진행 중 방 상태는 Redis의 relay:room:{roomCode}
 * 값만 생성/조회/갱신합니다. 이 클래스에서는 PostgreSQL artifact/gallery 결과물을 만들거나
 * MinIO/WebSocket을 호출하지 않습니다.
 */
@Service
public class RelayRoomServiceImpl implements RelayRoomService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 6;
    private static final int HOST_JOIN_ORDER = 0;
    private static final int ROOM_UPDATE_MAX_RETRIES = 3;
    private static final Set<Integer> ALLOWED_TIME_LIMIT_SECONDS = Set.of(30, 45, 60);
    // 재접속 유예 시간은 WebSocket 연결 해제 감지 시각(disconnectedAt)을 기준으로 계산합니다.
    private static final Duration RECONNECT_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String ROOM_FULL_MESSAGE = "방 정원이 가득 찼습니다.";
    private static final String GAME_IN_PROGRESS_MESSAGE = "게임이 진행 중입니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ROOM_UPDATE_CONFLICT_MESSAGE = "릴레이 방 상태를 갱신할 수 없습니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRoomRepository relayRoomRepository;

    /**
     * 릴레이 방 유스케이스 처리에 필요한 공통 사용자 해석기, 방코드 도구, Redis 저장소를 주입받습니다.
     */
    public RelayRoomServiceImpl(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        RelayRoomRepository relayRoomRepository) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRoomRepository = relayRoomRepository;
    }

    /**
     * 새 릴레이 방을 생성하는 메서드입니다.
     *
     * 요청 UUID가 이미 발급된 익명 사용자인지 확인하고, 방장으로 표시할 닉네임이 설정되어 있는지 검증합니다. 이후 Redis에 없는
     * roomCode를 발급하고, 생성자를 첫 번째 참여자이자 방장으로 포함한 WAITING 상태의 방을 Redis에 저장합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomCreateResponse createRoom(String userUuidValue) {
        // 클라이언트가 보낸 UUID로 새 사용자를 만들지 않고, 이미 발급된 익명 사용자만 허용합니다.
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);

        // 방 생성 시에도 기본 닉네임 사용자를 막아 대기실 표시 이름을 Redis에 안정적으로 복사합니다.
        validateNicknameRegistered(hostUser);

        // 중복 검사는 Redis key 존재 여부로 수행해 공유 링크/실시간 연결/저장 key가 같은 roomCode를 쓰게 합니다.
        String roomCode = roomCodeGenerator.generateUnique(relayRoomRepository::existsByRoomCode);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant hostParticipant = new RelayRoomParticipant(hostUser.getId().toString(),
            hostUser.getNickname(), true, HOST_JOIN_ORDER, true, null, now);
        RelayRoomState roomState = new RelayRoomState(roomCode, RelayRoomStatus.WAITING, hostUser.getId().toString(),
            DEFAULT_TIME_LIMIT_SECONDS, MIN_PARTICIPANTS, MAX_PARTICIPANTS, null, List.of(hostParticipant), now, now);

        relayRoomRepository.save(roomState);

        return RelayRoomCreateResponse.from(roomState);
    }

    /**
     * 현재 릴레이 방 상태를 조회하는 메서드입니다.
     *
     * 요청자를 기존 사용자로 검증하고 roomCode 형식을 확인한 뒤 Redis 방 상태를 읽습니다. 이 메서드는 조회 전용이므로 Redis
     * 값을 변경하지 않고, 요청자 기준의 viewer 상태만 계산해 응답 DTO로 변환합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        // 조회 API도 기존 익명 사용자만 사용할 수 있으므로 UUID 검증과 사용자 존재 확인을 먼저 수행합니다.
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        // 방코드 형식이 틀린 요청은 Redis 조회 전에 400으로 차단합니다.
        validateRoomCode(roomCodeValue);

        // Redis miss는 존재하지 않거나 만료된 진행 중 방으로 보고 404 응답으로 변환합니다.
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
        // 현재 API는 스냅샷 조회 전용이므로 Redis participants를 변경하지 않고 viewer 안내값만 계산합니다.
        RelayRoomViewerResponse viewer = createViewerResponse(viewerUser.getId().toString(), roomState,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return RelayRoomStateResponse.from(roomState, viewer);
    }

    /**
     * 릴레이 방 입장 또는 재접속 복귀를 처리하는 메서드입니다.
     *
     * 요청 사용자가 이미 participants에 있으면 중복 추가하지 않고 기존 참여자 흐름을 처리합니다. 요청 사용자가 아직 참여자가 아니면
     * WAITING 방인지, 정원이 남아 있는지, 닉네임이 설정되어 있는지 확인한 뒤 새 참여자를 Redis 상태에 추가합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse joinRoom(String userUuidValue, String roomCodeValue) {
        // UUID는 서버가 이미 발급한 익명 사용자만 허용하고, 이 API에서 새 사용자를 만들지 않습니다.
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        validateRoomCode(roomCodeValue);

        // 신규 입장/재접속은 Redis 상태를 바꾸므로 WATCH 기반 저장이 충돌하면 최신 상태를 다시 읽어 재시도합니다.
        for (int attempt = 0; attempt < ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = findRoomState(roomCodeValue);
            String viewerUserUuid = viewerUser.getId().toString();
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            Optional<RelayRoomParticipant> participant = findParticipant(roomState, viewerUserUuid);

            if (participant.isPresent()) {
                Optional<RelayRoomStateResponse> existingParticipantResponse = joinExistingParticipant(viewerUserUuid,
                    roomState, participant.get(), now);

                if (existingParticipantResponse.isPresent()) {
                    return existingParticipantResponse.get();
                }

                continue;
            }

            Optional<RelayRoomStateResponse> joinResponse = joinNewParticipant(viewerUser, roomState, now);

            if (joinResponse.isPresent()) {
                return joinResponse.get();
            }
        }

        throw new IllegalStateException(ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 방장이 대기실에서 파트별 제한 시간을 변경하는 메서드입니다.
     *
     * 설정은 Redis 방 상태의 timeLimitSeconds와 updatedAt만 바꾸며, 참가자/방장/상태/생성 시각 등 다른 필드는 기존
     * 값을 유지합니다. Redis 저장 충돌은 최신 상태를 다시 읽어 짧게 재시도합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        RelayRoomSettingsRequest request) {
        int timeLimitSeconds = resolveTimeLimitSeconds(request);
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = findRoomState(roomCodeValue);
            validateWaitingRoomForSettings(roomState);
            RelayRoomParticipant participant = findParticipant(roomState, viewerUserUuid)
                .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
            validateRoomHost(viewerUserUuid, roomState, participant);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomState updatedRoomState = updateRoomTimeLimit(roomState, timeLimitSeconds, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                RelayRoomViewerResponse viewer = createViewerResponse(viewerUserUuid, updatedRoomState, now);

                return RelayRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new IllegalStateException(ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * WebSocket 연결 성공을 Redis 참여자 연결 상태에 반영하는 메서드입니다.
     *
     * 요청 UUID는 기존 사용자로 확인하고, 이미 REST 입장/복귀 API를 통해 participants에 등록된 사용자만
     * connected=true로 갱신합니다. 이 메서드는 신규 참여자를 만들지 않습니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUser.getId().toString(), roomCodeValue, true);
    }

    /**
     * WebSocket 연결 해제를 Redis 참여자 연결 상태에 반영하는 메서드입니다.
     *
     * disconnect 이벤트는 이미 연결 때 검증된 세션에서 발생하므로 UUID 형식만 확인하고 DB 사용자 재조회는 하지 않습니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        String viewerUserUuid = anonymousUserResolver.parseUuid(userUuidValue).toString();
        validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUserUuid, roomCodeValue, false);
    }

    private RelayRoomState findRoomState(String roomCodeValue) {
        return relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    /**
     * 방 생성 또는 신규 입장 전에 공통 사용자 프로필의 닉네임이 실제 표시 가능한 값인지 검증합니다.
     *
     * 기본 닉네임인 "익명"이나 공백 닉네임은 대기실 참여자 목록에 표시할 수 없으므로 400으로 거절합니다. 기존 참여자의 재접속에서는
     * Redis에 저장된 참여자 닉네임을 유지하므로 이 검증을 다시 수행하지 않습니다.
     */
    private void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    /**
     * path variable로 받은 roomCode가 서비스에서 발급하는 6자리 방코드 규칙에 맞는지 검증합니다.
     *
     * 형식이 잘못된 방코드는 Redis 조회 전에 차단해 존재 여부와 무관하게 400 응답을 반환하도록 합니다.
     */
    private void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    private int resolveTimeLimitSeconds(RelayRoomSettingsRequest request) {
        if (request == null || request.timeLimitSeconds() == null
            || !ALLOWED_TIME_LIMIT_SECONDS.contains(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    private void validateWaitingRoomForSettings(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    private void validateRoomHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
        }
    }

    /**
     * Redis 방 상태의 참여자 목록에서 요청 UUID에 해당하는 기존 참여자를 찾습니다.
     *
     * 조회 API의 viewer 계산과 입장 API의 기존 참여자/idempotent/reconnect 분기에서 같은 기준을 쓰기 위한 공통
     * 탐색 메서드입니다.
     */
    private Optional<RelayRoomParticipant> findParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return roomState.participants().stream()
            .filter(roomParticipant -> roomParticipant.userUuid().equals(viewerUserUuid)).findFirst();
    }

    /**
     * 이미 방에 등록된 참여자의 입장 API 재호출을 처리합니다.
     *
     * 연결 중인 참여자는 멱등 호출로 보고 Redis 저장 없이 현재 상태를 반환합니다. 연결이 끊긴 참여자는 10초 유예 시간 안이면 해당
     * 참여자만 connected 상태로 복구하고, 유예 시간이 지나면 자동 제출 완료 상태로 보고 409를 반환합니다.
     */
    private Optional<RelayRoomStateResponse> joinExistingParticipant(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        // 이미 연결 중인 참여자의 재호출은 멱등 처리하며 Redis updatedAt도 변경하지 않습니다.
        if (participant.connected()) {
            RelayRoomViewerResponse viewer = createViewerResponse(viewerUserUuid, roomState, now);

            return Optional.of(RelayRoomStateResponse.from(roomState, viewer));
        }

        if (!canReconnect(participant, now)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }

        RelayRoomParticipant reconnectedParticipant = new RelayRoomParticipant(participant.userUuid(),
            participant.nickname(), participant.host(), participant.joinOrder(), true, null, participant.joinedAt());
        RelayRoomState updatedRoomState = replaceParticipant(roomState, reconnectedParticipant, now);

        if (!relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
            return Optional.empty();
        }

        RelayRoomViewerResponse viewer = createViewerResponse(viewerUserUuid, updatedRoomState, now);

        return Optional.of(RelayRoomStateResponse.from(updatedRoomState, viewer));
    }

    /**
     * 아직 방에 없는 사용자를 새 참여자로 추가합니다.
     *
     * 신규 입장은 WAITING 상태에서만 허용하며, 정원과 닉네임을 검증한 뒤 host=false, connected=true인 새 참여자를
     * 생성합니다. joinOrder는 기존 최댓값 다음 번호를 사용하고, 갱신된 방 상태를 Redis에 다시 저장합니다.
     */
    private Optional<RelayRoomStateResponse> joinNewParticipant(AppUser viewerUser, RelayRoomState roomState,
        LocalDateTime now) {
        validateJoinableRoom(roomState);
        validateNicknameRegistered(viewerUser);

        RelayRoomParticipant newParticipant = new RelayRoomParticipant(viewerUser.getId().toString(),
            viewerUser.getNickname(), false, nextJoinOrder(roomState), true, null, now);
        List<RelayRoomParticipant> participants = new ArrayList<>(roomState.participants());
        participants.add(newParticipant);
        RelayRoomState updatedRoomState = updateRoomParticipants(roomState, participants, now);

        if (!relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
            return Optional.empty();
        }

        RelayRoomViewerResponse viewer = createViewerResponse(viewerUser.getId().toString(), updatedRoomState, now);

        return Optional.of(RelayRoomStateResponse.from(updatedRoomState, viewer));
    }

    /**
     * 신규 사용자가 현재 방에 입장 가능한 상태인지 검증합니다.
     *
     * WAITING 방은 정원이 남아 있으면 통과하고, 정원이 가득 차면 409를 반환합니다. PLAYING 방은 신규 UUID 입장을
     * 차단하고, FINISHED/CLOSED 등 종료 상태는 모두 종료된 방으로 처리합니다.
     */
    private void validateJoinableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                throw new ConflictException(ROOM_FULL_MESSAGE);
            }

            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_IN_PROGRESS_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 신규 참여자에게 부여할 다음 입장 순서를 계산합니다.
     *
     * 중간에 이탈한 참여자의 순서를 재사용하지 않기 위해 현재 participants의 최대 joinOrder에 1을 더합니다. 참여자 목록이
     * 비어 있는 예외적인 상태에서도 0부터 시작하도록 기본값은 -1로 둡니다.
     */
    private int nextJoinOrder(RelayRoomState roomState) {
        return roomState.participants().stream().map(RelayRoomParticipant::joinOrder).max(Comparator.naturalOrder())
            .orElse(-1) + 1;
    }

    /**
     * 기존 참여자 1명의 상태만 교체한 새 Redis 방 상태를 만듭니다.
     *
     * record 기반 모델은 불변으로 다루기 때문에 참여자 객체를 직접 수정하지 않고, 대상 UUID의 참여자를 새 객체로 교체한 목록을
     * 만들어 방 상태 재생성 메서드에 전달합니다.
     */
    private RelayRoomState replaceParticipant(RelayRoomState roomState, RelayRoomParticipant updatedParticipant,
        LocalDateTime updatedAt) {
        List<RelayRoomParticipant> participants = roomState.participants().stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();

        return updateRoomParticipants(roomState, participants, updatedAt);
    }

    /**
     * 참여자 목록과 updatedAt만 바꾼 새 Redis 방 상태를 생성합니다.
     *
     * roomCode, status, hostUserUuid, 제한 시간, 최소/최대 인원, currentPart, createdAt은 기존
     * 값을 그대로 유지합니다. 신규 입장이나 재접속처럼 실제 Redis 상태가 바뀌는 경우에만 이 메서드를 사용합니다.
     */
    private RelayRoomState updateRoomParticipants(RelayRoomState roomState, List<RelayRoomParticipant> participants,
        LocalDateTime updatedAt) {
        return new RelayRoomState(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(),
            roomState.timeLimitSeconds(), roomState.minParticipants(), roomState.maxParticipants(),
            roomState.currentPart(), participants, roomState.createdAt(), updatedAt);
    }

    private RelayRoomState updateRoomTimeLimit(RelayRoomState roomState, int timeLimitSeconds,
        LocalDateTime updatedAt) {
        return new RelayRoomState(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(), timeLimitSeconds,
            roomState.minParticipants(), roomState.maxParticipants(), roomState.currentPart(), roomState.participants(),
            roomState.createdAt(), updatedAt);
    }

    private RelayRoomStateResponse updateParticipantConnectionState(String viewerUserUuid, String roomCodeValue,
        boolean connected) {
        for (int attempt = 0; attempt < ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = findRoomState(roomCodeValue);
            validateWebSocketConnectableRoom(roomState);
            RelayRoomParticipant participant = findParticipant(roomState, viewerUserUuid)
                .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomParticipant updatedParticipant = new RelayRoomParticipant(participant.userUuid(),
                participant.nickname(), participant.host(), participant.joinOrder(), connected, connected ? null : now,
                participant.joinedAt());
            RelayRoomState updatedRoomState = replaceParticipant(roomState, updatedParticipant, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                RelayRoomViewerResponse viewer = createViewerResponse(viewerUserUuid, updatedRoomState, now);

                return RelayRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new IllegalStateException(ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private void validateWebSocketConnectableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING || roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 요청 UUID 기준의 viewer 응답을 계산합니다.
     *
     * 요청자가 participants에 있으면 기존 참여자 viewer 정책을 적용하고, 없으면 신규 입장 가능 여부를 계산합니다. 이 메서드는
     * 응답 안내값만 만들며 Redis 상태를 변경하지 않습니다.
     */
    private RelayRoomViewerResponse createViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        LocalDateTime now) {
        // 요청자가 이미 방에 들어온 참여자인지 먼저 판별한 뒤 참여자/비참여자 정책을 분리합니다.
        Optional<RelayRoomParticipant> participant = findParticipant(roomState, viewerUserUuid);

        if (participant.isPresent()) {
            return createParticipantViewerResponse(viewerUserUuid, roomState, participant.get(), now);
        }

        return createNonParticipantViewerResponse(viewerUserUuid, roomState);
    }

    /**
     * 기존 참여자의 viewer 상태를 계산합니다.
     *
     * 연결 중이면 재접속이 필요 없으므로 canReconnect=false를 반환합니다. 연결이 끊긴 참여자는 disconnectedAt 기준
     * 10초 이내인지에 따라 canReconnect 또는 RECONNECT_EXPIRED 안내값을 반환합니다.
     */
    private RelayRoomViewerResponse createParticipantViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        // Redis의 host 플래그와 현재 hostUserUuid 중 하나라도 맞으면 방장으로 판단합니다.
        boolean host = participant.host() || roomState.hostUserUuid().equals(viewerUserUuid);

        // 연결 중인 기존 참여자는 입장/재접속 처리가 필요 없으므로 상태 안내만 반환합니다.
        if (participant.connected()) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false, null);
        }

        // 연결이 끊긴 기존 참여자만 10초 유예 시간 안에서 재접속 가능 여부를 계산합니다.
        if (canReconnect(participant, now)) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, true, null);
        }

        return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false,
            RelayRoomViewerBlockedReason.RECONNECT_EXPIRED);
    }

    /**
     * 연결이 끊긴 기존 참여자가 아직 재접속 유예 시간 안에 있는지 판단합니다.
     *
     * disconnectedAt이 없으면 기준 시각이 없으므로 재접속 불가로 봅니다. disconnectedAt + 10초가 현재 시각과 같거나
     * 이후이면 아직 복귀 가능한 상태입니다.
     */
    private boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        LocalDateTime disconnectedAt = participant.disconnectedAt();

        // disconnectedAt이 없으면 재접속 기준 시각을 알 수 없으므로 만료로 취급합니다.
        if (disconnectedAt == null) {
            return false;
        }

        // 정확히 10초가 지난 경계값까지는 재접속 가능으로 봅니다.
        return !disconnectedAt.plus(RECONNECT_GRACE_PERIOD).isBefore(now);
    }

    /**
     * 아직 참여자가 아닌 요청자의 viewer 상태를 계산합니다.
     *
     * 이 메서드는 실제 입장을 처리하지 않고 현재 방 상태 기준으로 canJoin과 blockedReason만 안내합니다. 실제 신규 입장
     * 처리는 joinNewParticipant 메서드에서 수행합니다.
     */
    private RelayRoomViewerResponse createNonParticipantViewerResponse(String viewerUserUuid,
        RelayRoomState roomState) {
        // 비참여자는 실제 입장 처리 없이 현재 방 상태 기준으로 입장 가능 안내값만 받습니다.
        RelayRoomViewerBlockedReason blockedReason = findJoinBlockedReason(roomState);
        boolean canJoin = blockedReason == null;

        return new RelayRoomViewerResponse(viewerUserUuid, false, false, canJoin, false, blockedReason);
    }

    /**
     * 비참여자가 현재 방에 새로 들어올 수 없는 이유를 계산합니다.
     *
     * WAITING 방에 자리가 있으면 null을 반환해 canJoin=true가 되도록 합니다. 정원 초과, 게임 진행 중, 결과 생성 완료,
     * 종료 상태는 각각 프론트가 표시할 수 있는 blockedReason 값으로 변환합니다.
     */
    private RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            // 대기실에서는 정원이 남아 있을 때만 신규 입장이 가능합니다.
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return RelayRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            // 게임이 시작된 뒤에는 신규 UUID 입장을 차단하고, 기존 참여자 재접속만 별도 API에서 처리합니다.
            return RelayRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == RelayRoomStatus.FINISHED) {
            // 결과 생성이 끝난 방은 더 이상 대기실 입장 대상으로 보지 않습니다.
            return RelayRoomViewerBlockedReason.ROOM_FINISHED;
        }

        // 그 밖의 종료 상태는 CLOSED로 안내합니다.
        return RelayRoomViewerBlockedReason.ROOM_CLOSED;
    }
}
