package com.nemonicworld.files.service;

import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;

/**
 * 파일 업로드 사전 준비 유스케이스를 정의합니다.
 */
public interface FileService {

    /**
     * MinIO 직접 업로드를 위한 pending 파일 메타데이터를 만들고 PUT presigned URL을 발급합니다.
     */
    FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request);

    /**
     * MinIO에 실제 객체가 업로드되었는지 확인하고 pending 파일을 uploaded 상태로 변경합니다.
     */
    FileConfirmResponse confirmUpload(String userUuidValue, String fileIdValue);

}
