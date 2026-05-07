from pydantic import BaseModel, Field


class CheckRequest(BaseModel):
    imageUrl: str = Field(
        description="검사 대상인 커뮤니티 게시용 최종 원본 이미지 URL",
        examples=["http://localhost:9000/nemonic-local/community/original.png"],
    )
    thumbnailUrl: str | None = Field(
        default=None,
        description="커뮤니티 목록과 공유 미리보기에 사용할 최종 썸네일 이미지 URL",
        examples=["http://localhost:9000/nemonic-local/community/thumbnail.png"],
    )
    clientText: str | None = Field(
        default=None,
        description="프론트가 알고 있는 텍스트박스 원문입니다. OCR 결과와 함께 검사 보조 입력으로 사용합니다.",
        examples=["안녕하세요 반갑습니다"],
    )
    sourceType: str = Field(
        description="커뮤니티 메모 출처입니다. DIRECT 또는 GALLERY를 사용합니다.",
        examples=["DIRECT"],
    )


class Category(BaseModel):
    label: str = Field(description="UnSmile 모델이 감지한 부적절 표현 카테고리", examples=["악플/욕설"])
    score: float = Field(description="카테고리 감지 점수", ge=0.0, le=1.0, examples=[0.87])


class CheckResponse(BaseModel):
    allowed: bool = Field(description="게시 허용 여부입니다. false이면 Spring Boot가 메모를 저장하지 않습니다.")
    ocrText: str | None = Field(
        default=None,
        description="검사에 사용한 텍스트입니다. 현재 MVP에서는 clientText와 동일합니다.",
    )
    categories: list[Category] = Field(default_factory=list, description="임계값 이상으로 감지된 차단 카테고리 목록")
    reason: str | None = Field(default=None, description="차단 사유 안내 문구")
