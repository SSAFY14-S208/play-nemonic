package com.nemonicworld.files.service.support;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileStorageSupport {

    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String MINIO_NO_SUCH_KEY_CODE = "NoSuchKey";
    private static final String MINIO_NO_SUCH_OBJECT_CODE = "NoSuchObject";

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final MinioStorageProperties properties;

    public FileStorageSupport(MinioClient minioClient, @Qualifier("publicMinioClient") MinioClient publicMinioClient,
        MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.properties = properties;
    }

    /**
     * MinIO에 직접 PUT 업로드할 수 있는 만료 시간 제한 URL을 생성합니다.
     */
    public String createPutPresignedUrl(String objectKey, String contentType, int expiresIn) {
        try {
            String presignedUrl = publicMinioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(properties.bucket()).object(objectKey)
                    .expiry(expiresIn).extraHeaders(Map.of("Content-Type", contentType)).build());

            return applyPublicPathPrefix(presignedUrl);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    /**
     * 비공개 파일을 직접 조회할 수 있는 만료 시간 제한 URL을 생성합니다.
     */
    public String createGetPresignedUrl(String objectKey, int expiresIn) {
        try {
            String presignedUrl = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(properties.bucket()).object(objectKey).expiry(expiresIn).build());

            return applyPublicPathPrefix(presignedUrl);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    public StatObjectResponse statObject(String objectKey) {
        try {
            return minioClient
                .statObject(StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                throw new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE);
            }

            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    public void removeObject(String objectKey) {
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

    /**
     * 공개 MinIO 주소가 /minio 같은 경로 접두사를 포함하면 사전 서명 URL 반환값에만 접두사를 붙입니다.
     */
    private String applyPublicPathPrefix(String presignedUrl) {
        URI publicUri = URI.create(properties.publicUrl());
        String publicPath = normalizePathPrefix(publicUri.getRawPath());
        if (!StringUtils.hasText(publicPath)) {
            return presignedUrl;
        }

        URI presignedUri = URI.create(presignedUrl);
        String publicOrigin = origin(publicUri);
        String presignedOrigin = origin(presignedUri);
        if (!publicOrigin.equals(presignedOrigin)) {
            return presignedUrl;
        }

        String query = presignedUri.getRawQuery() == null ? "" : "?%s".formatted(presignedUri.getRawQuery());
        String fragment = presignedUri.getRawFragment() == null ? "" : "#%s".formatted(presignedUri.getRawFragment());

        return "%s%s%s%s%s".formatted(publicOrigin, publicPath, presignedUri.getRawPath(), query, fragment);
    }

    private String normalizePathPrefix(String rawPath) {
        if (!StringUtils.hasText(rawPath) || "/".equals(rawPath)) {
            return "";
        }

        return rawPath.replaceAll("/+$", "");
    }

    private String origin(URI uri) {
        String port = uri.getPort() == -1 ? "" : ":%d".formatted(uri.getPort());

        return "%s://%s%s".formatted(uri.getScheme(), uri.getHost(), port);
    }

    private boolean isObjectNotFound(ErrorResponseException e) {
        String code = e.errorResponse().code();

        return MINIO_NO_SUCH_KEY_CODE.equals(code) || MINIO_NO_SUCH_OBJECT_CODE.equals(code);
    }
}
