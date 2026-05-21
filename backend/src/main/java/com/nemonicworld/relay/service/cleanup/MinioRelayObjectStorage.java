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
import org.springframework.util.StringUtils;

@Component
public class MinioRelayObjectStorage implements RelayObjectStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String MINIO_NO_SUCH_KEY_CODE = "NoSuchKey";
    private static final String MINIO_NO_SUCH_OBJECT_CODE = "NoSuchObject";
    private static final String RELAY_TEMP_OBJECT_KEY_PREFIX = "relay/tmp/";
    private static final String RELAY_RESULT_OBJECT_KEY_PREFIX = "relay/results/";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioRelayObjectStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public List<RelayStoredObject> findObjects(String prefix, LocalDateTime cutoff, int limit) {
        if (!isAllowedPrefix(prefix) || limit <= 0) {
            return List.of();
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(properties.bucket()).prefix(prefix).recursive(true).maxKeys(limit).build());
            List<RelayStoredObject> objects = new ArrayList<>();
            for (Result<Item> result : results) {
                if (objects.size() >= limit) {
                    break;
                }

                Item item = result.get();
                if (isOldObject(item, prefix, cutoff)) {
                    objects.add(new RelayStoredObject(item.objectName(), item.lastModified().toLocalDateTime()));
                }
            }

            return List.copyOf(objects);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        if (!isAllowedObjectKey(objectKey)) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE,
                new InternalServerException("릴레이 정리 대상 파일 경로가 아닙니다."));
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

    private boolean isOldObject(Item item, String prefix, LocalDateTime cutoff) {
        if (item == null || item.isDir() || item.lastModified() == null) {
            return false;
        }

        String objectName = item.objectName();
        if (!StringUtils.hasText(objectName) || !objectName.startsWith(prefix)) {
            return false;
        }

        return !item.lastModified().toLocalDateTime().isAfter(cutoff);
    }

    private boolean isAllowedPrefix(String prefix) {
        return RELAY_TEMP_OBJECT_KEY_PREFIX.equals(prefix) || RELAY_RESULT_OBJECT_KEY_PREFIX.equals(prefix);
    }

    private boolean isAllowedObjectKey(String objectKey) {
        return StringUtils.hasText(objectKey) && (objectKey.startsWith(RELAY_TEMP_OBJECT_KEY_PREFIX)
            || objectKey.startsWith(RELAY_RESULT_OBJECT_KEY_PREFIX));
    }

    private boolean isObjectNotFound(ErrorResponseException e) {
        String code = e.errorResponse().code();

        return MINIO_NO_SUCH_KEY_CODE.equals(code) || MINIO_NO_SUCH_OBJECT_CODE.equals(code);
    }
}
