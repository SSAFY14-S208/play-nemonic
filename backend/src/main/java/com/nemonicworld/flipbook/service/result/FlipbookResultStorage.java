package com.nemonicworld.flipbook.service.result;

/**
 * 플립북 프레임 이미지와 최종 GIF를 MinIO 등 외부 저장소에 읽고 씁니다.
 */
public interface FlipbookResultStorage {

    byte[] download(String objectKey);

    void upload(String objectKey, byte[] bytes, String contentType);
}
