package com.nemonicworld.relay.service.cleanup;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Override
    public List<String> findOldTempObjectKeys(LocalDateTime cutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        try {
            Iterable<Result<Item>> results = minioClient
                .listObjects(ListObjectsArgs.builder().bucket(properties.bucket()).prefix(RELAY_TEMP_OBJECT_KEY_PREFIX)
                    .recursive(true).maxKeys(limit).build());
            List<String> objectKeys = new ArrayList<>();
            for (Result<Item> result : results) {
                if (objectKeys.size() >= limit) {
                    break;
                }

                Item item = result.get();
                if (isOldRelayTempObject(item, cutoff)) {
                    objectKeys.add(item.objectName());
                }
            }

            return List.copyOf(objectKeys);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private void removeObject(String objectKey) {
        if (!objectKey.startsWith(RELAY_TEMP_OBJECT_KEY_PREFIX)) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE,
                new InternalServerException("릴레이 임시 파일 경로가 아닙니다."));
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

    private boolean isOldRelayTempObject(Item item, LocalDateTime cutoff) {
        if (item == null || item.isDir() || item.lastModified() == null) {
            return false;
        }

        String objectName = item.objectName();
        if (objectName == null || !objectName.startsWith(RELAY_TEMP_OBJECT_KEY_PREFIX)) {
            return false;
        }

        return !item.lastModified().toLocalDateTime().isAfter(cutoff);
    }
}
