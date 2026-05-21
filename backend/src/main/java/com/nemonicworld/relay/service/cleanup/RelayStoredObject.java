package com.nemonicworld.relay.service.cleanup;

import java.time.LocalDateTime;

public record RelayStoredObject(String objectKey, LocalDateTime lastModifiedAt) {
}
