package com.nemonicworld.files.service;

import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FilePresignResponse;

/**
 * 파일 업로드 사전 준비 유스케이스를 정의합니다.
 */
public interface FileService {

    /**
     * MinIO 직접 업로드를 위한 pending 파일 메타데이터를 만들고 PUT presigned URL을 발급합니다.
     */
    FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request);
}
