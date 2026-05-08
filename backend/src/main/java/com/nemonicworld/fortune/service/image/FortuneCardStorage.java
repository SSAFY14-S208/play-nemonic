package com.nemonicworld.fortune.service.image;

/**
 * 생성된 운세 카드 이미지를 객체 스토리지에 저장하는 경계입니다.
 */
public interface FortuneCardStorage {

    void upload(String objectKey, byte[] bytes, String contentType);
}
