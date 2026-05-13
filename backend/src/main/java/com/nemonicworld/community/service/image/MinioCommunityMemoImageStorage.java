package com.nemonicworld.community.service.image;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MinioCommunityMemoImageStorage implements CommunityMemoImageStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioCommunityMemoImageStorage.class);
    private static final String FILE_STORAGE_ERROR_MESSAGE = "Community memo image storage failed.";
    private static final String MINIO_NO_SUCH_KEY_CODE = "NoSuchKey";
    private static final String MINIO_NO_SUCH_OBJECT_CODE = "NoSuchObject";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioCommunityMemoImageStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public byte[] download(String objectKey) {
        try (InputStream inputStream = minioClient
            .getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void upload(String objectKey, byte[] bytes, String contentType) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                .contentType(contentType).stream(inputStream, bytes.length, -1).build());
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void deleteQuietly(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                log.debug("community memo derivative image already removed. objectKey={}", objectKey);
                return;
            }

            log.warn("community memo derivative image delete failed. objectKey={}", objectKey, e);
        } catch (Exception e) {
            log.warn("community memo derivative image delete failed. objectKey={}", objectKey, e);
        }
    }

    private boolean isObjectNotFound(ErrorResponseException e) {
        String code = e.errorResponse().code();

        return MINIO_NO_SUCH_KEY_CODE.equals(code) || MINIO_NO_SUCH_OBJECT_CODE.equals(code);
    }
}
