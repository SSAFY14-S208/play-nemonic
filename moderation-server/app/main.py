from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.moderation import classify_text, warm_up_model
from app.ocr import OcrError, extract_text_from_image_data, extract_text_from_image_url
from app.schemas import CheckRequest, CheckResponse

logger = logging.getLogger("nemonic.moderation")
LOG_TEXT_PREVIEW_LIMIT = 300


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # 서버 시작 시 모델을 미리 로딩해서 첫 검사 요청의 지연을 줄입니다.
    warm_up_model()
    yield


app = FastAPI(
    title="네모닉 커뮤니티 모더레이션 서버",
    description=(
        "Spring Boot 백엔드가 커뮤니티 메모를 저장하기 전에 호출하는 내부 FastAPI 서버입니다. "
        "외부 OCR API로 이미지 텍스트를 추출하고 clientText와 함께 UnSmile 모델로 검사합니다."
    ),
    version="0.1.0",
    lifespan=lifespan,
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    # Spring Boot 연동 중 요청 바디가 비었는지 바로 확인할 수 있도록 개발 단계에서 원문을 로그로 남깁니다.
    body = await request.body()
    logger.warning("요청 검증 실패 path=%s errors=%s body=%s", request.url.path, exc.errors(), body.decode("utf-8"))
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.get(
    "/health",
    tags=["상태"],
    summary="상태 확인",
    description="모더레이션 서버가 정상적으로 응답하는지 확인합니다.",
)
def health():
    return {"status": "ok"}


@app.post(
    "/check",
    response_model=CheckResponse,
    tags=["모더레이션"],
    summary="커뮤니티 메모 게시 전 검사",
    description=(
        "Spring Boot가 community_memo INSERT 전에 호출하는 내부 API입니다. "
        "게시 차단도 HTTP 200으로 응답하고 allowed=false로 표현합니다."
    ),
    response_description="모더레이션 검사 결과",
)
def check(request: CheckRequest):
    # 실제 게시 흐름에서는 Spring Boot가 만든 이미지 URL을 받아 OCR을 시도합니다.
    try:
        ocr_text = extract_text_from_image_url(request.imageUrl)
    except OcrError as exc:
        # OCR은 보조 신호로만 사용합니다. 호출 실패나 판독 실패가 있어도 clientText 검사는 계속 진행합니다.
        logger.warning("OCR 처리를 건너뜁니다. imageUrl=%s error=%s", request.imageUrl, exc)
        ocr_text = ""

    return _build_check_response(ocr_text, request.clientText)


@app.post(
    "/check-file",
    response_model=CheckResponse,
    tags=["모더레이션"],
    summary="로컬 이미지 파일 OCR 테스트",
    description=(
        "Swagger에서 로컬 이미지 파일을 직접 업로드해 OCR과 UnSmile 검사를 확인하는 개발용 API입니다. "
        "Spring Boot 연동용 메모 생성 API는 계속 /check를 사용합니다."
    ),
    response_description="모더레이션 검사 결과",
)
async def check_file(
    image: UploadFile = File(description="OCR을 테스트할 로컬 이미지 파일"),
    clientText: str | None = Form(default=None, description="이미지 OCR 결과와 함께 검사할 텍스트"),
    sourceType: str = Form(default="DIRECT", description="테스트용 출처 타입"),
):
    # 로컬 개발자가 Swagger에서 파일을 직접 올려 OCR 품질을 빠르게 확인하는 용도입니다.
    _ = sourceType
    try:
        ocr_text = extract_text_from_image_data(await image.read(), image.filename)
    except OcrError as exc:
        logger.warning("OCR 처리를 건너뜁니다. fileName=%s error=%s", image.filename, exc)
        ocr_text = ""

    return _build_check_response(ocr_text, clientText)


def _build_check_response(ocr_text: str, client_text: str | None) -> CheckResponse:
    text = _combine_texts(ocr_text, client_text)
    detected_categories = classify_text(text)
    allowed = len(detected_categories) == 0

    logger.info(
        "UnSmile 검사 완료 allowed=%s textLength=%d textPreview=%s categories=%s",
        allowed,
        len(text),
        _preview_text(text),
        [category.model_dump() for category in detected_categories],
    )

    # allowed=false는 UnSmile이 차단 라벨을 임계값 이상으로 감지한 경우에만 내려갑니다.
    return CheckResponse(
        allowed=allowed,
        ocrText=text,
        categories=detected_categories,
        reason=None if not detected_categories else "부적절한 표현이 감지되었습니다.",
    )


def _combine_texts(ocr_text: str, client_text: str | None) -> str:
    # OCR 결과와 프론트가 알고 있는 텍스트박스 원문을 함께 검사합니다.
    parts = [ocr_text.strip(), (client_text or "").strip()]
    return "\n".join(part for part in parts if part)


def _preview_text(text: str) -> str:
    # 로그가 여러 줄로 깨지지 않도록 공백을 정리하고, 과도하게 긴 원문은 앞부분만 남깁니다.
    compact_text = " ".join(text.split())
    if len(compact_text) <= LOG_TEXT_PREVIEW_LIMIT:
        return compact_text

    return f"{compact_text[:LOG_TEXT_PREVIEW_LIMIT]}..."
