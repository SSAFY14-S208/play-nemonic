package com.nemonicworld.common.openapi;

import java.util.List;

/**
 * Swagger UI 문서 그룹 이름, 표시명, path 매칭 규칙, 태그 구성을 관리합니다.
 */
public final class OpenApiGroups {

    public static final String ALL = "all";
    public static final String ALL_DISPLAY_NAME = "전체 API";
    public static final List<String> ALL_PATHS = List.of("/api/v1/**", "/api/logs/**");
    public static final List<String> ALL_TAGS = OpenApiTags.orderedTagNames();

    public static final String COMMON = "common";
    public static final String COMMON_DISPLAY_NAME = "공통";
    public static final List<String> COMMON_PATHS = List.of("/api/v1/users/**", "/api/v1/auth/**", "/api/v1/invites/**",
        "/api/v1/files/**");
    public static final List<String> COMMON_TAGS = List.of(OpenApiTags.AUTH, OpenApiTags.USER, OpenApiTags.INVITE,
        OpenApiTags.FILE);

    public static final String CONTENTS = "contents";
    public static final String CONTENTS_DISPLAY_NAME = "콘텐츠";
    public static final List<String> CONTENTS_PATHS = List.of("/api/v1/gallery", "/api/v1/gallery/**",
        "/api/v1/artifacts/**", "/api/v1/share", "/api/v1/share/**", "/api/v1/community/memos",
        "/api/v1/community/memos/**", "/api/v1/fortune", "/api/v1/fortune/**");
    public static final List<String> CONTENTS_TAGS = List.of(OpenApiTags.GALLERY, OpenApiTags.ARTIFACT,
        OpenApiTags.SHARE, OpenApiTags.COMMUNITY, OpenApiTags.FORTUNE);

    public static final String GAMES = "games";
    public static final String GAMES_DISPLAY_NAME = "게임";
    public static final List<String> GAMES_PATHS = List.of("/api/v1/relay/rooms", "/api/v1/relay/rooms/**",
        "/api/v1/flipbook/rooms", "/api/v1/flipbook/rooms/**");
    public static final List<String> GAMES_TAGS = List.of(OpenApiTags.RELAY, OpenApiTags.FLIPBOOK);

    public static final String SUPPORT_LOGS = "support-logs";
    public static final String SUPPORT_LOGS_DISPLAY_NAME = "문의·로그";
    public static final List<String> SUPPORT_LOGS_PATHS = List.of("/api/v1/inquiries", "/api/v1/inquiries/**",
        "/api/logs/**");
    public static final List<String> SUPPORT_LOGS_TAGS = List.of(OpenApiTags.CS_INQUIRY, OpenApiTags.CLIENT_LOG);

    public static final String BACKOFFICE = "backoffice";
    public static final String BACKOFFICE_DISPLAY_NAME = "백오피스";
    public static final List<String> BACKOFFICE_PATHS = List.of("/api/v1/admins", "/api/v1/admins/**",
        "/api/v1/admin/**", "/api/v1/backoffice/**");
    public static final List<String> BACKOFFICE_TAGS = List.of(OpenApiTags.ADMIN, OpenApiTags.ADMIN_COMMUNITY,
        OpenApiTags.ADMIN_INQUIRY, OpenApiTags.SYSTEM_PARAMETER, OpenApiTags.BACKOFFICE_RELAY,
        OpenApiTags.BACKOFFICE_FLIPBOOK, OpenApiTags.GMS_PROMPT);

    public static final List<String> ORDERED_GROUPS = List.of(ALL, COMMON, CONTENTS, GAMES, SUPPORT_LOGS, BACKOFFICE);
    public static final List<String> ORDERED_DISPLAY_NAMES = List.of(ALL_DISPLAY_NAME, COMMON_DISPLAY_NAME,
        CONTENTS_DISPLAY_NAME, GAMES_DISPLAY_NAME, SUPPORT_LOGS_DISPLAY_NAME, BACKOFFICE_DISPLAY_NAME);
    public static final List<String> ORDERED_API_DOCS_URLS = ORDERED_GROUPS.stream()
        .map(group -> "/v3/api-docs/" + group).toList();

    private OpenApiGroups() {
    }
}
