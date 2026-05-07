from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # MODERATION_THRESHOLD 같은 환경변수를 자동으로 읽습니다.
    model_config = SettingsConfigDict(env_prefix="MODERATION_")

    model_name: str = "smilegate-ai/kor_unsmile"
    threshold: float = 0.5
    model_max_length: int = 512


@lru_cache
def get_settings() -> Settings:
    return Settings()
