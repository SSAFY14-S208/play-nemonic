package com.nemonicworld.share.service;

import com.nemonicworld.share.dto.request.ShareCreateRequest;
import com.nemonicworld.share.dto.response.ShareCreateResponse;

public interface ShareService {

    /**
     * SNS 공유에 필요한 산출물 이미지 URL과 플랫폼별 유입 URL을 생성합니다.
     */
    ShareCreateResponse createShare(String userUuidValue, ShareCreateRequest request);
}
