package com.nemonicworld.artifact.service.download;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
public class MinioArtifactDownloadStorage implements ArtifactDownloadStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioArtifactDownloadStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());

            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }

            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
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
}
