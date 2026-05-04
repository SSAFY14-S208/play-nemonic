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

    private OpenApiErrorExamples() {
    }
}
