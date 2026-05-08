from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_FILE = Path(__file__).resolve().parents[1] / ".env"


class Settings(BaseSettings):
    # 실행 위치가 달라도 moderation-server/.env 파일을 안정적으로 읽습니다.
    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8-sig",
        extra="ignore",
    )

    model_name: str = Field(default="smilegate-ai/kor_unsmile", validation_alias="MODERATION_MODEL_NAME")
    # threshold가 낮을수록 더 민감하게 차단하고, 높을수록 명확한 유해 표현만 차단합니다.
    threshold: float = Field(default=0.5, validation_alias="MODERATION_THRESHOLD")
    model_max_length: int = Field(default=512, validation_alias="MODERATION_MODEL_MAX_LENGTH")
    # 운영 전환 전에는 OCR 장애가 게시 자체를 막지 않도록 필요한 환경에서만 켭니다.
    ocr_enabled: bool = Field(default=False, validation_alias="MODERATION_OCR_ENABLED")
    google_vision_api_key: str | None = Field(default=None, validation_alias="MODERATION_GOOGLE_VISION_API_KEY")
    google_vision_endpoint: str = Field(
        default="https://vision.googleapis.com/v1/images:annotate",
        validation_alias="MODERATION_GOOGLE_VISION_ENDPOINT",
    )
    google_vision_feature_type: str = Field(
        default="DOCUMENT_TEXT_DETECTION",
        validation_alias="MODERATION_GOOGLE_VISION_FEATURE_TYPE",
    )
    google_vision_language_hint: str = Field(default="ko", validation_alias="MODERATION_GOOGLE_VISION_LANGUAGE_HINT")
    google_vision_timeout_seconds: float = Field(
        default=5.0,
        validation_alias="MODERATION_GOOGLE_VISION_TIMEOUT_SECONDS",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
