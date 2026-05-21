package com.nemonicworld.relay.service.submission;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 릴레이 제출 이미지를 MinIO 임시 경로에 저장합니다.
 */
@Component
public class MinioRelaySubmissionStorage implements RelaySubmissionStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioRelaySubmissionStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void upload(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                .contentType(file.getContentType()).stream(inputStream, file.getSize(), -1).build());
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }
}
