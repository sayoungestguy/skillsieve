"""All settings in one place (CODING_STANDARDS §4). Env vars are listed in TECHNICAL_DESIGN §8."""

from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: SecretStr = Field(description="postgresql://… (asyncpg DSN, sslmode=require)")
    internal_token: SecretStr = Field(min_length=16, description="Shared with core-api")
    gemini_api_key: SecretStr | None = Field(
        default=None, description="AI Studio key; empty = extraction disabled (503)"
    )
    gemini_model: str = "gemini-3.5-flash-lite"
    prompt_version: str = "extract-v1"
    llm_rpm: int = Field(default=4, ge=1, description="Calls per minute (under the free limit)")
    llm_rpd: int = Field(default=200, ge=1, description="Calls per day (Pacific day, as Gemini)")
    llm_timeout_s: float = 8.0
    llm_budget_s: float = Field(
        default=9.0, description="Whole extraction incl. retries; core-api waits 10 s"
    )
    max_input_chars: int = 12_000
    fuzzy_min_score: float = 92.0
    db_pool_max: int = 3
    taxonomy_refresh_s: float = Field(
        default=300.0, description="Reload aliases (core-api's Flyway seed may run after we start)"
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()  # values come from the environment
