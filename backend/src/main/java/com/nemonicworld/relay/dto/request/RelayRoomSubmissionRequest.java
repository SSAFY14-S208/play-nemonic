package com.nemonicworld.relay.dto.request;

import org.springframework.web.multipart.MultipartFile;

/**
 * 릴레이 현재 파트 제출 요청입니다.
 */
public record RelayRoomSubmissionRequest(Integer canvasIndex, String part, MultipartFile drawingImage,
    MultipartFile hintImage) {
}
