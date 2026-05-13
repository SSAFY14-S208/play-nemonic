package com.nemonicworld.common.openapi;

import java.util.List;

/**
 * Swagger UI 문서 그룹 이름, 표시명, path 매칭 규칙, 태그 구성을 관리합니다.
 */
public final class OpenApiGroups {

    public static final String ALL = "all";
    public static final String COMMON = "common";
    public static final String CONTENTS = "contents";
    public static final String GAMES = "games";
    public static final String SUPPORT_LOGS = "support-logs";
    public static final String BACKOFFICE = "backoffice";

    public static final GroupDefinition ALL_GROUP = new GroupDefinition(ALL, "전체 API",
        List.of("/api/v1/**", "/api/logs/**"), OpenApiTags.orderedTagNames());
    public static final GroupDefinition COMMON_GROUP = new GroupDefinition(COMMON, "공통",
        List.of("/api/v1/users/**", "/api/v1/auth/**", "/api/v1/invites/**", "/api/v1/files/**"),
        List.of(OpenApiTags.AUTH, OpenApiTags.USER, OpenApiTags.INVITE, OpenApiTags.FILE));
    public static final GroupDefinition CONTENTS_GROUP = new GroupDefinition(CONTENTS, "콘텐츠",
        List.of("/api/v1/gallery", "/api/v1/gallery/**", "/api/v1/artifacts/**", "/api/v1/share", "/api/v1/share/**",
            "/api/v1/community/memos", "/api/v1/community/memos/**", "/api/v1/fortune", "/api/v1/fortune/**"),
        List.of(OpenApiTags.GALLERY, OpenApiTags.ARTIFACT, OpenApiTags.SHARE, OpenApiTags.COMMUNITY,
            OpenApiTags.FORTUNE));
    public static final GroupDefinition GAMES_GROUP = new GroupDefinition(GAMES, "게임",
        List.of("/api/v1/relay/rooms", "/api/v1/relay/rooms/**", "/api/v1/flipbook/rooms", "/api/v1/flipbook/rooms/**"),
        List.of(OpenApiTags.RELAY, OpenApiTags.FLIPBOOK));
    public static final GroupDefinition SUPPORT_LOGS_GROUP = new GroupDefinition(SUPPORT_LOGS, "문의·로그",
        List.of("/api/v1/inquiries", "/api/v1/inquiries/**", "/api/logs/**"),
        List.of(OpenApiTags.CS_INQUIRY, OpenApiTags.CLIENT_LOG));
    public static final GroupDefinition BACKOFFICE_GROUP = new GroupDefinition(BACKOFFICE, "백오피스",
        List.of("/api/v1/admins", "/api/v1/admins/**", "/api/v1/admin/**", "/api/v1/backoffice/**"),
        List.of(OpenApiTags.ADMIN, OpenApiTags.ADMIN_COMMUNITY, OpenApiTags.ADMIN_INQUIRY, OpenApiTags.SYSTEM_PARAMETER,
            OpenApiTags.BACKOFFICE_RELAY, OpenApiTags.BACKOFFICE_FLIPBOOK, OpenApiTags.GMS_PROMPT));

    public static final List<GroupDefinition> ORDERED_GROUPS = List.of(ALL_GROUP, COMMON_GROUP, CONTENTS_GROUP,
        GAMES_GROUP, SUPPORT_LOGS_GROUP, BACKOFFICE_GROUP);

    public static List<String> orderedDisplayNames() {
        return ORDERED_GROUPS.stream().map(GroupDefinition::displayName).toList();
    }

    public static List<String> orderedApiDocsUrls() {
        return ORDERED_GROUPS.stream().map(GroupDefinition::apiDocsUrl).toList();
    }

    private OpenApiGroups() {
    }

    public record GroupDefinition(String name, String displayName, List<String> pathsToMatch, List<String> tagNames) {

        public String apiDocsUrl() {
            return "/v3/api-docs/" + name;
        }
    }
}
