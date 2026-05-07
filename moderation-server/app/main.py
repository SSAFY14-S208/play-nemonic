from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.moderation import classify_text, warm_up_model
from app.schemas import CheckRequest, CheckResponse


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # 서버 시작 시 모델을 미리 로딩해서 첫 검사 요청의 지연을 줄입니다.
    warm_up_model()
    yield


app = FastAPI(
    title="네모닉 커뮤니티 모더레이션 서버",
    description=(
        "Spring Boot 백엔드가 커뮤니티 메모를 저장하기 전에 호출하는 내부 FastAPI 서버입니다. "
        "현재 MVP는 OCR 없이 clientText를 UnSmile 모델로 검사합니다."
    ),
    version="0.1.0",
    lifespan=lifespan,
)


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
    # OCR을 붙이기 전까지는 프론트가 전달한 텍스트박스 원문만 검사합니다.
    text = (request.clientText or "").strip()
    detected_categories = classify_text(text)

    return CheckResponse(
        allowed=len(detected_categories) == 0,
        ocrText=text,
        categories=detected_categories,
        reason=None if not detected_categories else "부적절한 표현이 감지되었습니다.",
    )
