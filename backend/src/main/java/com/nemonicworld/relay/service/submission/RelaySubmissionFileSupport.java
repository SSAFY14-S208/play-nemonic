package com.nemonicworld.relay.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RelaySubmissionFileSupport {

    private static final String HINT_IMAGE_REQUIRED_MESSAGE = "힌트 이미지가 필요합니다.";
    private static final String INVALID_FILE_SIZE_MESSAGE = "파일 크기가 올바르지 않습니다.";
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "지원하지 않는 파일 형식입니다.";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp");

    private final MinioStorageProperties minioStorageProperties;
    private final RelaySubmissionStorage relaySubmissionStorage;

    public RelaySubmissionFileSupport(MinioStorageProperties minioStorageProperties,
        RelaySubmissionStorage relaySubmissionStorage) {
        this.minioStorageProperties = minioStorageProperties;
        this.relaySubmissionStorage = relaySubmissionStorage;
    }

    public MultipartFile resolveHintImage(RelayRoomSubmissionRequest request, RelayDrawingPart requestedPart) {
        if (requestedPart == RelayDrawingPart.LEGS) {
            return null;
        }

        MultipartFile hintImage = request.hintImage();
        if (hintImage == null || hintImage.isEmpty()) {
            throw new BadRequestException(HINT_IMAGE_REQUIRED_MESSAGE);
        }

        validateFile(hintImage);

        return hintImage;
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BadRequestException(INVALID_FILE_SIZE_MESSAGE);
        }

        if (file.getSize() > minioStorageProperties.maxUploadByteSize()) {
            throw new PayloadTooLargeException(createFileSizeExceededMessage());
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException(UNSUPPORTED_FILE_TYPE_MESSAGE);
        }
    }

    public String createObjectKey(String roomCode, int canvasIndex, RelayDrawingPart part, boolean hint) {
        String fileName = part.name().toLowerCase(Locale.ROOT) + (hint ? "-hint.png" : ".png");

        return "relay/tmp/%s/%d/%s".formatted(roomCode, canvasIndex, fileName);
    }

    public void uploadSubmissionImages(String drawingObjectKey, MultipartFile drawingImage, String hintObjectKey,
        MultipartFile hintImage) {
        relaySubmissionStorage.upload(drawingObjectKey, drawingImage);

        if (hintImage != null) {
            relaySubmissionStorage.upload(hintObjectKey, hintImage);
        }
    }

    private String createFileSizeExceededMessage() {
        long maxSizeMb = minioStorageProperties.maxUploadByteSize() / 1024 / 1024;

        return "파일 크기가 제한을 초과했습니다 (최대 %dMB).".formatted(maxSizeMb);
    }
}
