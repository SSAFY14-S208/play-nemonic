package com.nemonicworld.files.service;

import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.dto.response.FileViewUrlResponse;

/**
 * 파일 업로드 사전 준비와 완료 확인, 삭제 유스케이스를 정의합니다.
 */
public interface FileService {

    /**
     * MinIO 직접 업로드를 위한 대기 상태 파일 메타데이터를 생성하고 PUT 사전 서명 URL을 발급합니다.
     */
    FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request);

    /**
     * 업로드 완료된 비공개 파일을 조회하기 위한 GET 사전 서명 URL을 발급합니다.
     */
    FileViewUrlResponse createViewUrl(String userUuidValue, String fileIdValue);

    /**
     * MinIO에 실제 객체가 업로드되었는지 확인하고 대기 상태 파일을 업로드 완료 상태로 변경합니다.
     */
    FileConfirmResponse confirmUpload(String userUuidValue, String fileIdValue);

    /**
     * 대기 상태 파일 업로드를 취소하고 MinIO 객체와 DB 메타데이터를 삭제 상태로 변경합니다.
     */
    FileDeleteResponse deleteUpload(String userUuidValue, String fileIdValue);

}
