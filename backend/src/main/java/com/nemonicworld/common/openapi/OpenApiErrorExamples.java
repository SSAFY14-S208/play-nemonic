package com.nemonicworld.common.openapi;

/**
 * Swagger/OpenAPI 실패 응답 예시를 모아 둔 상수입니다.
 */
public final class OpenApiErrorExamples {

    public static final String INVALID_UUID = """
        {
          "success": false,
          "message": "유효하지 않은 UUID 형식입니다."
        }
        """;
    public static final String USER_NOT_FOUND = """
        {
          "success": false,
          "message": "존재하지 않는 사용자입니다."
        }
        """;
    public static final String INVALID_NICKNAME = """
        {
          "success": false,
          "message": "닉네임은 1자 이상 10자 이하로 입력해주세요."
        }
        """;
    public static final String RELAY_NICKNAME_REQUIRED = """
        {
          "success": false,
          "message": "닉네임을 먼저 설정해주세요."
        }
        """;
    public static final String FLIPBOOK_NICKNAME_REQUIRED = """
        {
          "success": false,
          "message": "닉네임을 먼저 설정해주세요."
        }
        """;
    public static final String INVALID_ROOM_CODE = """
        {
          "success": false,
          "message": "유효하지 않은 방코드입니다."
        }
        """;
    public static final String INVALID_INVITE_CODE = """
        {
          "success": false,
          "message": "유효하지 않은 초대코드 형식입니다."
        }
        """;
    public static final String INVITE_NOT_FOUND = """
        {
          "success": false,
          "message": "초대코드를 찾을 수 없습니다."
        }
        """;
    public static final String INVITE_EXPIRED = """
        {
          "success": false,
          "message": "만료된 초대코드입니다."
        }
        """;
    public static final String INVITE_ROOM_CLOSED = """
        {
          "success": false,
          "message": "이미 종료된 방입니다."
        }
        """;
    public static final String INVITE_ROOM_FULL = """
        {
          "success": false,
          "message": "정원이 가득 찬 방입니다."
        }
        """;
    public static final String RELAY_ROOM_NOT_FOUND = """
        {
          "success": false,
          "message": "존재하지 않는 방입니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_NOT_FOUND = """
        {
          "success": false,
          "message": "존재하지 않는 방입니다."
        }
        """;
    public static final String RELAY_ROOM_FULL = """
        {
          "success": false,
          "message": "방 정원이 가득 찼습니다."
        }
        """;
    public static final String RELAY_GAME_IN_PROGRESS = """
        {
          "success": false,
          "message": "게임이 진행 중입니다."
        }
        """;
    public static final String RELAY_KICKED_ROOM_REJOIN = """
        {
          "success": false,
          "message": "강퇴된 방에는 다시 입장할 수 없습니다."
        }
        """;
    public static final String RELAY_RECONNECT_EXPIRED = """
        {
          "success": false,
          "message": "이미 자동 제출 처리되었습니다."
        }
        """;
    public static final String RELAY_ROOM_CLOSED = """
        {
          "success": false,
          "message": "이미 종료된 방입니다."
        }
        """;
    public static final String RELAY_INVALID_TIME_LIMIT_SECONDS = """
        {
          "success": false,
          "message": "제한 시간은 30초, 45초, 60초 중 하나여야 합니다."
        }
        """;
    public static final String RELAY_ROOM_PARTICIPANT_REQUIRED = """
        {
          "success": false,
          "message": "릴레이 방에 참여하지 않은 사용자입니다."
        }
        """;
    public static final String RELAY_ROOM_HOST_REQUIRED = """
        {
          "success": false,
          "message": "방장만 사용할 수 있습니다."
        }
        """;
    public static final String RELAY_ROOM_CLOSE_HOST_REQUIRED = """
        {
          "success": false,
          "message": "방장만 사용할 수 있는 기능입니다."
        }
        """;
    public static final String RELAY_ROOM_KICK_HOST_REQUIRED = """
        {
          "success": false,
          "message": "방장만 사용할 수 있는 기능입니다."
        }
        """;
    public static final String RELAY_KICK_TARGET_NOT_FOUND = """
        {
          "success": false,
          "message": "강퇴할 참여자를 찾을 수 없습니다."
        }
        """;
    public static final String RELAY_CLOSE_BEFORE_RESULT = """
        {
          "success": false,
          "message": "결과 생성 전에는 방을 종료할 수 없습니다."
        }
        """;
    public static final String RELAY_CLOSE_WHILE_PLAYING = """
        {
          "success": false,
          "message": "게임 진행 중에는 방을 종료할 수 없습니다."
        }
        """;
    public static final String RELAY_CLOSE_WHILE_FINALIZING = """
        {
          "success": false,
          "message": "결과 생성 중에는 방을 종료할 수 없습니다."
        }
        """;
    public static final String RELAY_WAITING_ROOM_SETTINGS_ONLY = """
        {
          "success": false,
          "message": "대기 중인 방에서만 설정을 변경할 수 있습니다."
        }
        """;
    public static final String RELAY_WAITING_ROOM_KICK_ONLY = """
        {
          "success": false,
          "message": "대기실에서만 강퇴할 수 있습니다."
        }
        """;
    public static final String RELAY_WAITING_ROOM_LEAVE_ONLY = """
        {
          "success": false,
          "message": "대기실에서만 퇴장할 수 있습니다."
        }
        """;
    public static final String RELAY_SELF_KICK_NOT_ALLOWED = """
        {
          "success": false,
          "message": "자기 자신은 강퇴할 수 없습니다."
        }
        """;
    public static final String RELAY_HOST_KICK_NOT_ALLOWED = """
        {
          "success": false,
          "message": "방장은 강퇴할 수 없습니다."
        }
        """;
    public static final String FLIPBOOK_INVALID_TIME_LIMIT_SECONDS = """
        {
          "success": false,
          "message": "제한 시간은 30초, 45초, 60초 중 하나여야 합니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_PARTICIPANT_REQUIRED = """
        {
          "success": false,
          "message": "플립북 방에 참여하지 않은 사용자입니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_HOST_REQUIRED = """
        {
          "success": false,
          "message": "방장만 사용할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_KICK_HOST_REQUIRED = """
        {
          "success": false,
          "message": "방장만 사용할 수 있는 기능입니다."
        }
        """;
    public static final String FLIPBOOK_KICK_TARGET_NOT_FOUND = """
        {
          "success": false,
          "message": "강퇴할 참여자를 찾을 수 없습니다."
        }
        """;
    public static final String FLIPBOOK_WAITING_ROOM_SETTINGS_ONLY = """
        {
          "success": false,
          "message": "대기 중인 방에서만 설정을 변경할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_WAITING_ROOM_KICK_ONLY = """
        {
          "success": false,
          "message": "대기실에서만 강퇴할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_WAITING_ROOM_LEAVE_ONLY = """
        {
          "success": false,
          "message": "대기실에서만 퇴장할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_SELF_KICK_NOT_ALLOWED = """
        {
          "success": false,
          "message": "자기 자신은 강퇴할 수 없습니다."
        }
        """;
    public static final String FLIPBOOK_HOST_KICK_NOT_ALLOWED = """
        {
          "success": false,
          "message": "방장은 강퇴할 수 없습니다."
        }
        """;
    public static final String FLIPBOOK_KICKED_ROOM_REJOIN = """
        {
          "success": false,
          "message": "강퇴된 방에는 다시 입장할 수 없습니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_UPDATE_CONFLICT = """
        {
          "success": false,
          "message": "동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요."
        }
        """;
    public static final String FLIPBOOK_ROOM_START_UPDATE_CONFLICT = """
        {
          "success": false,
          "message": "동시 게임 시작 요청이 많아 플립북 방 시작 상태를 갱신하지 못했습니다. 다시 시도해주세요."
        }
        """;
    public static final String FLIPBOOK_GAME_ALREADY_STARTED = """
        {
          "success": false,
          "message": "이미 게임이 시작되었습니다."
        }
        """;
    public static final String FLIPBOOK_NOT_ENOUGH_PARTICIPANTS = """
        {
          "success": false,
          "message": "최소 2명이 모여야 시작할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_PARTICIPANTS_DISCONNECTED = """
        {
          "success": false,
          "message": "모든 참여자가 웹소켓에 연결되어야 게임을 시작할 수 있습니다."
        }
        """;
    public static final String FLIPBOOK_ROOM_CLOSED = """
        {
          "success": false,
          "message": "이미 종료된 방입니다."
        }
        """;
    public static final String RELAY_GAME_ALREADY_STARTED = """
        {
          "success": false,
          "message": "이미 게임이 시작되었습니다."
        }
        """;
    public static final String RELAY_NOT_ENOUGH_PARTICIPANTS = """
        {
          "success": false,
          "message": "최소 2명이 모여야 시작할 수 있습니다."
        }
        """;
    public static final String RELAY_PARTICIPANTS_DISCONNECTED = """
        {
          "success": false,
          "message": "모든 참여자가 연결된 상태에서만 시작할 수 있습니다."
        }
        """;
    public static final String RELAY_GAME_NOT_STARTED = """
        {
          "success": false,
          "message": "게임이 아직 시작되지 않았습니다."
        }
        """;
    public static final String RELAY_CURRENT_ASSIGNMENT_NOT_FOUND = """
        {
          "success": false,
          "message": "현재 배정된 그림이 없습니다."
        }
        """;
    public static final String RELAY_RESULT_ACCESS_DENIED = """
        {
          "success": false,
          "message": "릴레이 결과를 조회할 권한이 없습니다."
        }
        """;
    public static final String RELAY_RESULT_NOT_FOUND = """
        {
          "success": false,
          "message": "릴레이 결과를 찾을 수 없습니다."
        }
        """;
    public static final String INVALID_BIRTH_INFO = """
        {
          "success": false,
          "message": "생년월일 정보 형식이 올바르지 않습니다."
        }
        """;
    public static final String BIRTH_INFO_ALREADY_REGISTERED = """
        {
          "success": false,
          "message": "이미 생년월일 정보가 등록되어 있습니다."
        }
        """;
    public static final String BIRTH_INFO_NOT_REGISTERED = """
        {
          "success": false,
          "message": "등록된 생년월일 정보가 없습니다."
        }
        """;
    public static final String INVALID_GALLERY_ID = """
        {
          "success": false,
          "message": "유효하지 않은 갤러리 항목 ID 형식입니다."
        }
        """;
    public static final String GALLERY_ITEM_NOT_FOUND = """
        {
          "success": false,
          "message": "존재하지 않는 갤러리 항목입니다."
        }
        """;
    public static final String COMMUNITY_MEMO_NOT_FOUND = """
        {
          "success": false,
          "message": "존재하지 않는 커뮤니티 메모입니다."
        }
        """;
    public static final String UNSUPPORTED_COMMUNITY_MEMO_SOURCE_TYPE = """
        {
          "success": false,
          "message": "지원하지 않는 커뮤니티 메모 sourceType입니다."
        }
        """;
    public static final String INVALID_COMMUNITY_MEMO_SOURCE = """
        {
          "success": false,
          "message": "커뮤니티 메모 원본 정보가 올바르지 않습니다."
        }
        """;
    public static final String INVALID_COMMUNITY_MEMO_POSITION = """
        {
          "success": false,
          "message": "커뮤니티 메모 위치 정보가 올바르지 않습니다."
        }
        """;
    public static final String INVALID_COMMUNITY_MEMO_DECORATION = """
        {
          "success": false,
          "message": "커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."
        }
        """;
    public static final String SHARE_IMAGE_NOT_FOUND = """
        {
          "success": false,
          "message": "공유할 이미지 URL이 없습니다."
        }
        """;
    public static final String INVALID_PAGE_REQUEST = """
        {
          "success": false,
          "message": "페이지 요청 값이 올바르지 않습니다."
        }
        """;
    public static final String INVALID_FILE_ID = """
        {
          "success": false,
          "message": "유효하지 않은 fileId 형식입니다."
        }
        """;
    public static final String INVALID_FILE_NAME = """
        {
          "success": false,
          "message": "파일명이 올바르지 않습니다."
        }
        """;
    public static final String INVALID_BYTE_SIZE = """
        {
          "success": false,
          "message": "파일 크기가 올바르지 않습니다."
        }
        """;
    public static final String UNSUPPORTED_FILE_TYPE = """
        {
          "success": false,
          "message": "지원하지 않는 파일 형식입니다."
        }
        """;
    public static final String UNSUPPORTED_PURPOSE = """
        {
          "success": false,
          "message": "지원하지 않는 purpose입니다."
        }
        """;
    public static final String FILE_UPLOAD_NOT_FOUND = """
        {
          "success": false,
          "message": "파일 업로드 정보를 찾을 수 없습니다."
        }
        """;
    public static final String FILE_ACCESS_DENIED = """
        {
          "success": false,
          "message": "파일에 접근할 권한이 없습니다."
        }
        """;
    public static final String FILE_UPLOAD_STATUS_CONFLICT = """
        {
          "success": false,
          "message": "확인할 수 없는 파일 업로드 상태입니다."
        }
        """;
    public static final String FILE_DELETE_STATUS_CONFLICT = """
        {
          "success": false,
          "message": "삭제할 수 없는 파일 업로드 상태입니다."
        }
        """;
    public static final String FILE_SIZE_EXCEEDED = """
        {
          "success": false,
          "message": "파일 크기가 제한을 초과했습니다 (최대 10MB)."
        }
        """;
    public static final String FILE_STORAGE_ERROR = """
        {
          "success": false,
          "message": "파일 저장소 처리 중 오류가 발생했습니다."
        }
        """;
    public static final String SERVER_ERROR = """
        {
          "success": false,
          "message": "서버 오류가 발생했습니다."
        }
        """;
    public static final String BAD_REQUEST = """
        {
          "success": false,
          "message": "잘못된 요청입니다."
        }
        """;
    public static final String ADMIN_AUTHENTICATION_FAILED = """
        {
          "success": false,
          "message": "관리자 인증에 실패했습니다."
        }
        """;
    public static final String ADMIN_UNAUTHORIZED = """
        {
          "success": false,
          "message": "인증이 필요합니다."
        }
        """;
    public static final String ADMIN_SUPER_ADMIN_REQUIRED = """
        {
          "success": false,
          "message": "슈퍼 관리자 권한이 필요합니다."
        }
        """;
    public static final String ADMIN_LOGIN_ID_DUPLICATED = """
        {
          "success": false,
          "message": "이미 등록된 관리자 아이디입니다."
        }
        """;

    public static final String ADMIN_ACCOUNT_NOT_FOUND = """
        {
          "success": false,
          "message": "관리자 계정을 찾을 수 없습니다."
        }
        """;
    public static final String ADMIN_SELF_DELETE_FORBIDDEN = """
        {
          "success": false,
          "message": "자기 자신은 삭제할 수 없습니다."
        }
        """;
    public static final String ADMIN_SUPER_DELETE = """
        {
          "success": false,
          "message": "슈퍼 관리자 계정은 삭제할 수 없습니다."
        }
        """;

    private OpenApiErrorExamples() {
    }
}
