package com.nemonicworld.files.service.support;

import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FileUploadEventLogger {

    public void logCommunityFileEvent(String eventName, UUID userUuid, FileUpload fileUpload,
        Map<String, Object> extraMetadata) {
        if (!fileUpload.hasPurpose(FileUploadPurpose.COMMUNITY)) {
            return;
        }

        logFileEvent(eventName, "community_file", userUuid, fileUpload, extraMetadata);
    }

    public void logFileEvent(String eventName, UUID userUuid, FileUpload fileUpload,
        Map<String, Object> extraMetadata) {
        logFileEvent(eventName, "file_upload", userUuid, fileUpload, extraMetadata);
    }

    private void logFileEvent(String eventName, String contentType, UUID userUuid, FileUpload fileUpload,
        Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = StructuredEventLogger.metadata("file_id", fileUpload.getId(), "purpose",
            fileUpload.getPurpose(), "status", fileUpload.getStatus(), "object_key_hash",
            StructuredEventLogger.sha256Prefix(fileUpload.getObjectKey()), "content_type", fileUpload.getContentType(),
            "byte_size", fileUpload.getByteSize());
        metadata.putAll(extraMetadata == null ? Map.of() : extraMetadata);
        StructuredEventLogger.apiBusiness(eventName, contentType, userUuid.toString(), metadata);
    }
}
