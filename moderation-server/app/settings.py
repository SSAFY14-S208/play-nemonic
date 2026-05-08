from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # MODERATION_THRESHOLD 같은 환경변수와 moderation-server/.env 파일 값을 자동으로 읽습니다.
    # pydantic-settings는 dotenv 값을 읽을 때 원래 환경변수 이름도 함께 볼 수 있어 extra 입력은 무시합니다.
    model_config = SettingsConfigDict(
        env_prefix="MODERATION_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    model_name: str = "smilegate-ai/kor_unsmile"
    # threshold가 낮을수록 더 민감하게 차단하고, 높을수록 명확한 유해 표현만 차단합니다.
    threshold: float = 0.5
    model_max_length: int = 512
    # 운영 전환 전에는 OCR 장애가 게시 자체를 막지 않도록 필요한 환경에서만 켭니다.
    ocr_enabled: bool = False
    google_vision_api_key: str | None = None
    google_vision_endpoint: str = "https://vision.googleapis.com/v1/images:annotate"
    google_vision_feature_type: str = "DOCUMENT_TEXT_DETECTION"
    google_vision_language_hint: str = "ko"
    google_vision_timeout_seconds: float = 5.0


@lru_cache
def get_settings() -> Settings:
    return Settings()
