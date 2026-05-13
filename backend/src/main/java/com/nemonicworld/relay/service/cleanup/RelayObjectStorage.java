package com.nemonicworld.relay.service.cleanup;

import java.time.LocalDateTime;
import java.util.List;

public interface RelayObjectStorage {

    List<RelayStoredObject> findObjects(String prefix, LocalDateTime cutoff, int limit);

    void deleteObject(String objectKey);
}
