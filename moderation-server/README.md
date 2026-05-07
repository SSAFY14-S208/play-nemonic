# 네모닉 커뮤니티 모더레이션 서버

커뮤니티 메모가 공용 벽에 게시되기 전에 부적절한 텍스트를 검사하는 FastAPI 서버입니다.
Spring Boot 백엔드가 `POST /check`를 내부 호출하고, 통과한 경우에만 `community_memo`를 저장합니다.

현재 MVP는 OCR 없이 `clientText`만 UnSmile 모델로 검사합니다.

## 로컬 설치

```bash
python -m venv .venv
./.venv/Scripts/python.exe -m pip install --upgrade pip
./.venv/Scripts/python.exe -m pip install -r requirements.txt
```

## 실행

```bash
./.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

첫 실행 시 UnSmile 모델을 다운로드합니다.

## API

```http
GET /health
POST /check
```

`POST /check`는 허용과 차단 모두 HTTP 200으로 응답합니다. Spring 백엔드는 non-2xx 응답을 모더레이션 서버 장애로 보기 때문에, 실제 차단 여부는 `allowed: false`로 표현합니다.

## 환경변수

| 이름 | 기본값 |
| --- | --- |
| `MODERATION_MODEL_NAME` | `smilegate-ai/kor_unsmile` |
| `MODERATION_THRESHOLD` | `0.5` |
| `MODERATION_MODEL_MAX_LENGTH` | `512` |
