package com.nemonicworld.share.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "SNS 공유 정보 생성 요청")
public class ShareCreateRequest {

    @Schema(description = "공유할 갤러리 항목 UUID", example = "8d25f3a5-3c5a-4f21-9f54-68fa4a402011")
    @NotBlank
    private final String galleryId;

    @Schema(description = "UTM 캠페인명. 생략 시 결과물 종류 기반으로 생성됩니다.", example = "relay_drawing_result")
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "campaign은 영문, 숫자, _, -만 사용할 수 있습니다.")
    private final String campaign;

    @JsonCreator
    public ShareCreateRequest(@JsonProperty("galleryId") String galleryId, @JsonProperty("campaign") String campaign) {
        this.galleryId = galleryId;
        this.campaign = campaign;
    }

    public String galleryId() {
        return galleryId;
    }

    public String campaign() {
        return campaign;
    }
}
