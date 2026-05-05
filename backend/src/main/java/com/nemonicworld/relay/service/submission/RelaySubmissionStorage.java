package com.nemonicworld.relay.service.submission;

import org.springframework.web.multipart.MultipartFile;

/**
 * 릴레이 제출 이미지를 임시 저장소에 업로드합니다.
 */
public interface RelaySubmissionStorage {

    void upload(String objectKey, MultipartFile file);
}
