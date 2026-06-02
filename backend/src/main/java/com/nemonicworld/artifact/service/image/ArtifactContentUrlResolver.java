package com.nemonicworld.artifact.service.image;

import com.nemonicworld.artifact.dto.response.ArtifactContentUrlResponse;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ArtifactContentUrlResolver {

    private static final String KIND_FORTUNE = "fortune";
    private static final String KIND_RELAY_DRAWING = "relay_drawing";
    private static final String KIND_FLIPBOOK = "flipbook";
    private static final String KIND_INFINITE_CANVAS = "infinite_canvas";
    private static final String KIND_PHONE = "phone";
    private static final String KIND_COMMUNITY_MEMO = "community_memo";
    private static final String CONTENT_TYPE_FORTUNE_IMAGE = "fortune_image";
    private static final String CONTENT_TYPE_COMBINED_PREVIEW = "combined_preview";
    private static final String CONTENT_TYPE_GIF = "gif";
    private static final String CONTENT_TYPE_FIRST_IMAGE = "first_image";
    private static final String CONTENT_TYPE_CANVAS_IMAGE = "canvas_image";
    private static final String CONTENT_TYPE_PHONE_IMAGE = "phone_image";
    private static final String CONTENT_TYPE_COMMUNITY_MEMO_IMAGE = "community_memo_image";
    private static final String CONTENT_TYPE_THUMBNAIL = "thumbnail";

    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public ArtifactContentUrlResolver(MinioPublicUrlResolver minioPublicUrlResolver) {
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    public List<ArtifactContentUrlResponse> createContents(ArtifactImageUrlRow row) {
        List<ArtifactContentUrlResponse> contents = new ArrayList<>();
        String kind = row.kind();

        if (KIND_FORTUNE.equals(kind)) {
            addContent(contents, CONTENT_TYPE_FORTUNE_IMAGE, firstText(row.fortuneImageUrl(), row.thumbnailUrl()));
        } else if (KIND_RELAY_DRAWING.equals(kind)) {
            addContent(contents, CONTENT_TYPE_COMBINED_PREVIEW,
                firstText(row.relayCombinedPreviewUrl(), row.thumbnailUrl()));
        } else if (KIND_FLIPBOOK.equals(kind)) {
            addContent(contents, CONTENT_TYPE_GIF, row.flipbookGifUrl());
            addContent(contents, CONTENT_TYPE_FIRST_IMAGE, row.flipbookFirstImageUrl());
        } else if (KIND_INFINITE_CANVAS.equals(kind)) {
            addContent(contents, CONTENT_TYPE_CANVAS_IMAGE,
                firstText(row.infiniteCanvasImageUrl(), row.thumbnailUrl()));
        } else if (KIND_PHONE.equals(kind)) {
            addContent(contents, CONTENT_TYPE_PHONE_IMAGE, firstText(row.phoneImageUrl(), row.thumbnailUrl()));
        } else if (KIND_COMMUNITY_MEMO.equals(kind)) {
            addContent(contents, CONTENT_TYPE_COMMUNITY_MEMO_IMAGE, row.thumbnailUrl());
        }

        if (contents.isEmpty()) {
            addContent(contents, CONTENT_TYPE_THUMBNAIL, row.thumbnailUrl());
        }

        return contents;
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private void addContent(List<ArtifactContentUrlResponse> contents, String type, String objectReference) {
        String url = minioPublicUrlResolver.resolve(objectReference);
        if (StringUtils.hasText(url)) {
            contents.add(new ArtifactContentUrlResponse(type, url));
        }
    }
}
