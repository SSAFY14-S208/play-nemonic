package com.nemonicworld.common.openapi;

import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;

/**
 * Swagger UI에 노출되는 API 카테고리 이름과 표시 순서를 관리합니다.
 */
public final class OpenApiTags {

    public static final String AUTH = "인증";
    public static final String AUTH_DESCRIPTION = "로그인 및 인증 토큰 API";

    public static final String USER = "사용자";
    public static final String USER_DESCRIPTION = "익명 사용자 식별 및 정보 API";

    public static final String INVITE = "초대";
    public static final String INVITE_DESCRIPTION = "초대 코드 및 초대 링크 API";

    public static final String FILE = "파일 업로드";
    public static final String FILE_DESCRIPTION = "공통 파일 업로드 메타데이터 및 MinIO 업로드 URL API";

    public static final String GALLERY = "갤러리";
    public static final String GALLERY_DESCRIPTION = "갤러리 산출물 조회 및 폰 그림 저장 API";

    public static final String ARTIFACT = "산출물";
    public static final String ARTIFACT_DESCRIPTION = "체험 결과 산출물 조회 API";

    public static final String SHARE = "공유";
    public static final String SHARE_DESCRIPTION = "SNS 공유 이벤트 기록 API";

    public static final String COMMUNITY = "커뮤니티";
    public static final String COMMUNITY_DESCRIPTION = "커뮤니티 메모 작성 및 조회 API";

    public static final String FORTUNE = "오늘의 운세";
    public static final String FORTUNE_DESCRIPTION = "익명 사용자 운세 생성 및 조회 API";

    public static final String RELAY = "릴레이";
    public static final String RELAY_DESCRIPTION = "릴레이 드로잉 방 생성, 참여, 진행 API";

    public static final String FLIPBOOK = "플립북";
    public static final String FLIPBOOK_DESCRIPTION = "플립북 방 생성, 참여, 진행 API";

    public static final String INFINITE_CANVAS = "무한 캔버스";
    public static final String INFINITE_CANVAS_DESCRIPTION = "무한 캔버스 방 생성, 참여, 실시간 협업 API";

    public static final String CS_INQUIRY = "고객 문의";
    public static final String CS_INQUIRY_DESCRIPTION = "익명 사용자 고객 문의 API";

    public static final String CLIENT_LOG = "클라이언트 로그";
    public static final String CLIENT_LOG_DESCRIPTION = "프론트엔드 클라이언트 로그 수집 API";

    public static final String ADMIN = "백오피스 - 관리자";
    public static final String ADMIN_DESCRIPTION = "백오피스 관리자 계정 API";

    public static final String ADMIN_COMMUNITY = "백오피스 - 커뮤니티";
    public static final String ADMIN_COMMUNITY_DESCRIPTION = "백오피스 커뮤니티 메모 검토 API";

    public static final String ADMIN_INQUIRY = "백오피스 - 문의";
    public static final String ADMIN_INQUIRY_DESCRIPTION = "백오피스 고객 문의 관리 API";

    public static final String SYSTEM_PARAMETER = "백오피스 - 시스템 설정";
    public static final String SYSTEM_PARAMETER_DESCRIPTION = "백오피스 시스템 파라미터 조회 및 수정 API";

    public static final String BACKOFFICE_RELAY = "백오피스 - 릴레이 방";
    public static final String BACKOFFICE_RELAY_DESCRIPTION = "백오피스 활성 릴레이 방 조회 API";

    public static final String BACKOFFICE_FLIPBOOK = "백오피스 - 플립북 방";
    public static final String BACKOFFICE_FLIPBOOK_DESCRIPTION = "백오피스 활성 플립북 방 조회 API";

    public static final String BACKOFFICE_INFINITE_CANVAS = "백오피스 - 무한 캔버스";
    public static final String BACKOFFICE_INFINITE_CANVAS_DESCRIPTION = "백오피스 활성 무한 캔버스 조회 API";

    public static final String GMS_PROMPT = "백오피스 - GMS 프롬프트";
    public static final String GMS_PROMPT_DESCRIPTION = "백오피스 GMS 프롬프트 관리 API";

    private OpenApiTags() {
    }

    public static List<Tag> orderedTags() {
        return List.of(tag(USER, USER_DESCRIPTION), tag(INVITE, INVITE_DESCRIPTION), tag(FILE, FILE_DESCRIPTION),
            tag(GALLERY, GALLERY_DESCRIPTION), tag(ARTIFACT, ARTIFACT_DESCRIPTION), tag(SHARE, SHARE_DESCRIPTION),
            tag(COMMUNITY, COMMUNITY_DESCRIPTION), tag(FORTUNE, FORTUNE_DESCRIPTION), tag(RELAY, RELAY_DESCRIPTION),
            tag(FLIPBOOK, FLIPBOOK_DESCRIPTION), tag(CS_INQUIRY, CS_INQUIRY_DESCRIPTION),
            tag(INFINITE_CANVAS, INFINITE_CANVAS_DESCRIPTION), tag(CLIENT_LOG, CLIENT_LOG_DESCRIPTION),
            tag(AUTH, AUTH_DESCRIPTION), tag(ADMIN, ADMIN_DESCRIPTION),
            tag(ADMIN_COMMUNITY, ADMIN_COMMUNITY_DESCRIPTION), tag(ADMIN_INQUIRY, ADMIN_INQUIRY_DESCRIPTION),
            tag(SYSTEM_PARAMETER, SYSTEM_PARAMETER_DESCRIPTION), tag(BACKOFFICE_RELAY, BACKOFFICE_RELAY_DESCRIPTION),
            tag(BACKOFFICE_FLIPBOOK, BACKOFFICE_FLIPBOOK_DESCRIPTION),
            tag(BACKOFFICE_INFINITE_CANVAS, BACKOFFICE_INFINITE_CANVAS_DESCRIPTION),
            tag(GMS_PROMPT, GMS_PROMPT_DESCRIPTION));
    }

    public static List<String> orderedTagNames() {
        return orderedTags().stream().map(Tag::getName).toList();
    }

    public static List<Tag> orderedTags(List<String> tagNames) {
        return orderedTags().stream().filter(tag -> tagNames.contains(tag.getName())).toList();
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
