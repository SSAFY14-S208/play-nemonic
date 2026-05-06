package com.nemonicworld.relay.service.cleanup;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.files.config.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 릴레이 임시 이미지를 MinIO에서 hard delete합니다.
 */
@Component
public class MinioRelayTempFileStorage implements RelayTempFileStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String MINIO_NO_SUCH_KEY_CODE = "NoSuchKey";
    private static final String MINIO_NO_SUCH_OBJECT_CODE = "NoSuchObject";
    private static final String RELAY_TEMP_OBJECT_KEY_PREFIX = "relay/tmp/";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioRelayTempFileStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void deleteObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            removeObject(objectKey);
        }
    }

    private void removeObject(String objectKey) {
        if (!objectKey.startsWith(RELAY_TEMP_OBJECT_KEY_PREFIX)) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE,
                new IllegalArgumentException("릴레이 임시 파일 경로가 아닙니다."));
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                return;
            }

            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private boolean isObjectNotFound(ErrorResponseException e) {
        String code = e.errorResponse().code();

        return MINIO_NO_SUCH_KEY_CODE.equals(code) || MINIO_NO_SUCH_OBJECT_CODE.equals(code);
    }
}
