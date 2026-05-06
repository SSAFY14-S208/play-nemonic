package com.nemonicworld.relay.service.cleanup;

import java.util.List;

/**
 * 릴레이 임시 이미지 object를 실제 저장소에서 삭제합니다.
 */
public interface RelayTempFileStorage {

    void deleteObjects(List<String> objectKeys);
}
