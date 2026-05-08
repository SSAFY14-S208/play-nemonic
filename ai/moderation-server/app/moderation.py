from functools import lru_cache
from typing import Any

from transformers import pipeline

from app.schemas import Category
from app.settings import get_settings

# clean 계열 라벨은 게시 허용으로 보고, 아래 라벨만 임계값 이상일 때 차단합니다.
BLOCK_LABELS = {
    "여성/가족",
    "남성",
    "성소수자",
    "인종/국적",
    "연령",
    "지역",
    "종교",
    "기타 혐오",
    "악플/욕설",
}


@lru_cache
def get_classifier():
    settings = get_settings()
    # top_k=None으로 모든 라벨 점수를 받아 임계값 기반 다중 라벨 판정을 합니다.
    return pipeline(
        "text-classification",
        model=settings.model_name,
        tokenizer=settings.model_name,
        top_k=None,
        function_to_apply="sigmoid",
    )


def warm_up_model() -> None:
    # 애플리케이션 시작 시 한 번 호출해 모델 다운로드/로딩을 앞당깁니다.
    get_classifier()


def classify_text(text: str) -> list[Category]:
    normalized = text.strip()
    if not normalized:
        return []

    settings = get_settings()
    raw_scores = get_classifier()(
        normalized,
        truncation=True,
        max_length=settings.model_max_length,
    )
    # transformers 버전에 따라 반환 모양이 list 또는 list[list]일 수 있어 한 번 정규화합니다.
    scores = _normalize_pipeline_output(raw_scores)

    return [
        Category(label=item["label"], score=float(item["score"]))
        for item in scores
        if item["label"] in BLOCK_LABELS and float(item["score"]) >= settings.threshold
    ]


def _normalize_pipeline_output(raw_scores: Any) -> list[dict[str, Any]]:
    # 단건 입력 + top_k=None 조합에서는 보통 첫 원소에 라벨 목록이 들어옵니다.
    if raw_scores and isinstance(raw_scores[0], list):
        return raw_scores[0]

    return raw_scores
