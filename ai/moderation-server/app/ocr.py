import base64
import re
from typing import Any
from urllib.parse import urlparse

import httpx

from app.settings import get_settings

MIN_MEANINGFUL_TEXT_LENGTH = 2


class OcrError(RuntimeError):
    """OCR 실패를 모더레이션 실패와 분리해서 다루기 위한 예외입니다."""

    pass


def extract_text_from_image_url(image_url: str) -> str:
    settings = get_settings()
    if not settings.ocr_enabled:
        raise OcrError("OCR 설정이 비활성화되어 있습니다.")

    # /check는 Spring Boot가 넘겨준 MinIO 이미지 URL을 FastAPI가 먼저 읽고, 이미지 바이트를 Google Vision에 보냅니다.
    # 이렇게 해야 Google 서버가 접근할 수 없는 localhost/private URL도 로컬 개발 환경에서 OCR 테스트가 됩니다.
    _ensure_google_settings()
    image_data = _download_image_url(image_url)
    payload = _build_google_data_payload(image_data)
    return _request_google_vision(payload)


def extract_text_from_image_data(image_data: bytes, _image_name: str | None) -> str:
    settings = get_settings()
    if not settings.ocr_enabled:
        raise OcrError("OCR 설정이 비활성화되어 있습니다.")

    # /check-file은 Swagger 테스트용이므로 이미지 바이트를 base64로 변환해 Google Vision에 보냅니다.
    _ensure_google_settings()
    if not image_data:
        raise OcrError("OCR 테스트 이미지 파일이 비어 있습니다.")

    payload = _build_google_data_payload(image_data)
    return _request_google_vision(payload)


def _ensure_google_settings() -> None:
    settings = get_settings()
    if not settings.google_vision_api_key:
        raise OcrError("Google Cloud Vision API key 설정이 누락되었습니다.")


def _request_google_vision(payload: dict[str, Any]) -> str:
    settings = get_settings()
    timeout = httpx.Timeout(settings.google_vision_timeout_seconds)

    try:
        # API key 방식은 Google Vision REST API에 key 쿼리 파라미터를 붙여 호출합니다.
        response = httpx.post(
            settings.google_vision_endpoint,
            params={"key": settings.google_vision_api_key},
            json=payload,
            timeout=timeout,
        )
        response.raise_for_status()
        return _extract_google_vision_text(response.json())
    except httpx.HTTPStatusError as exc:
        response_text = exc.response.text[:500]
        message = f"Google Vision OCR 호출에 실패했습니다. status={exc.response.status_code}, body={response_text}"
        raise OcrError(message) from exc
    except httpx.RequestError as exc:
        raise OcrError(f"Google Vision OCR 요청을 전송하지 못했습니다. error={exc.__class__.__name__}") from exc
    except httpx.HTTPError as exc:
        raise OcrError("Google Vision OCR 호출에 실패했습니다.") from exc
    except ValueError as exc:
        raise OcrError("Google Vision OCR 응답 JSON을 해석할 수 없습니다.") from exc


def _download_image_url(image_url: str) -> bytes:
    parsed = urlparse(image_url)
    if parsed.scheme not in {"http", "https"}:
        raise OcrError("OCR 이미지 URL은 http 또는 https 형식이어야 합니다.")

    settings = get_settings()
    timeout = httpx.Timeout(settings.google_vision_timeout_seconds)
    try:
        response = httpx.get(image_url, timeout=timeout, follow_redirects=True)
        response.raise_for_status()
    except httpx.HTTPStatusError as exc:
        raise OcrError(f"OCR 이미지 다운로드에 실패했습니다. status={exc.response.status_code}") from exc
    except httpx.RequestError as exc:
        raise OcrError(f"OCR 이미지 다운로드 요청을 전송하지 못했습니다. error={exc.__class__.__name__}") from exc

    image_data = response.content
    if not image_data:
        raise OcrError("OCR 이미지 다운로드 결과가 비어 있습니다.")

    return image_data


def _build_google_data_payload(image_data: bytes) -> dict[str, Any]:
    return _build_google_payload({"content": base64.b64encode(image_data).decode("ascii")})


def _build_google_payload(image: dict[str, Any]) -> dict[str, Any]:
    settings = get_settings()
    # DOCUMENT_TEXT_DETECTION은 일반 TEXT_DETECTION보다 문서/손글씨 문맥을 더 길게 돌려받는 데 유리합니다.
    request: dict[str, Any] = {
        "image": image,
        "features": [{"type": settings.google_vision_feature_type}],
    }
    if settings.google_vision_language_hint:
        request["imageContext"] = {"languageHints": [settings.google_vision_language_hint]}

    return {"requests": [request]}


def _extract_google_vision_text(response_body: dict[str, Any]) -> str:
    responses = response_body.get("responses") or []
    if not responses:
        raise OcrError("Google Vision OCR 응답에 responses가 없습니다.")

    first_response = responses[0]
    error = first_response.get("error")
    if error:
        message = error.get("message") or "원인을 알 수 없습니다."
        raise OcrError(f"Google Vision OCR 분석이 실패했습니다. message={message}")

    # fullTextAnnotation을 우선 사용하고, 없으면 textAnnotations 첫 번째 설명값으로 한 번 더 보완합니다.
    full_text = first_response.get("fullTextAnnotation", {}).get("text")
    if not full_text:
        text_annotations = first_response.get("textAnnotations") or []
        if text_annotations:
            full_text = text_annotations[0].get("description")

    ocr_text = (full_text or "").strip()
    if not _has_meaningful_text(ocr_text):
        raise OcrError("Google Vision OCR이 의미 있는 텍스트를 추출하지 못했습니다.")

    return ocr_text


def _has_meaningful_text(text: str) -> bool:
    # 기호만 검출된 경우는 실질적인 OCR 결과로 보지 않습니다.
    meaningful_chars = re.findall(r"[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ]", text)
    return len(meaningful_chars) >= MIN_MEANINGFUL_TEXT_LENGTH
