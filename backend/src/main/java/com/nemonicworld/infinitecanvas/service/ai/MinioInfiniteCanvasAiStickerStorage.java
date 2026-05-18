package com.nemonicworld.infinitecanvas.service.ai;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

@Component
public class MinioInfiniteCanvasAiStickerStorage implements InfiniteCanvasAiStickerStorage {

    private static final String FILE_STORAGE_ERROR_MESSAGE = "AI 스티커 이미지를 저장할 수 없습니다.";

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioInfiniteCanvasAiStickerStorage(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void upload(String objectKey, byte[] bytes, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType(contentType).build());
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }
}
