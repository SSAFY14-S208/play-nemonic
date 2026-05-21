package com.nemonicworld.fortune.service.image;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

/**
 * 운세 카드 이미지를 MinIO에 영구 저장합니다.
 */
@Component
public class MinioFortuneCardStorage implements FortuneCardStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioFortuneCardStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
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
