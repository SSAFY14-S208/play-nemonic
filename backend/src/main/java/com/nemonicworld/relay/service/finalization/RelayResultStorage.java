package com.nemonicworld.relay.service.finalization;

/**
 * 릴레이 임시 파트 이미지와 최종 결과 이미지를 MinIO 등 외부 저장소에 읽고 씁니다.
 */
public interface RelayResultStorage {

    byte[] download(String objectKey);

    void upload(String objectKey, byte[] bytes, String contentType);

    void delete(String objectKey);
}
