package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * 릴레이 최종화에 필요한 MinIO object 읽기/쓰기만 담당합니다.
 */
@Component
public class MinioRelayResultStorage implements RelayResultStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioRelayResultStorage(MinioClient minioClient, MinioStorageProperties properties) {
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
}
