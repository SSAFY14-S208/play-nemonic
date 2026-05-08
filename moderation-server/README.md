# 네모닉 커뮤니티 모더레이션 서버

커뮤니티 메모가 공용 벽에 게시되기 전에 텍스트를 검사하는 FastAPI 서버입니다. Spring Boot 백엔드가 `POST /check`를 내부 호출하고, `allowed=true`일 때만 `community_memo`를 저장합니다.

OCR은 Google Cloud Vision API를 보조 신호로 사용합니다. OCR 호출 실패, 설정 누락, 판독 실패가 발생해도 게시를 바로 차단하지 않고 OCR 결과를 빈 값으로 처리한 뒤 `clientText`만 UnSmile 모델로 검사합니다.

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
POST /check-file
```

`POST /check`는 허용과 차단 모두 HTTP 200으로 응답합니다. Spring 백엔드는 실제 차단 여부를 `allowed=false`로 판단합니다.

`POST /check-file`은 FastAPI 단독 OCR 테스트용 API입니다. Swagger UI에서 로컬 이미지 파일을 직접 업로드하면 FastAPI가 파일 내용을 base64로 Google Vision API에 전달합니다. Spring Boot 연동용 메모 생성 흐름은 계속 `POST /check`를 사용합니다.

## 환경변수

| 이름 | 기본값 |
| --- | --- |
| `MODERATION_MODEL_NAME` | `smilegate-ai/kor_unsmile` |
| `MODERATION_THRESHOLD` | `0.5` |
| `MODERATION_MODEL_MAX_LENGTH` | `512` |
| `MODERATION_OCR_ENABLED` | `false` |
| `MODERATION_GOOGLE_VISION_API_KEY` | 없음 |
| `MODERATION_GOOGLE_VISION_ENDPOINT` | `https://vision.googleapis.com/v1/images:annotate` |
| `MODERATION_GOOGLE_VISION_FEATURE_TYPE` | `DOCUMENT_TEXT_DETECTION` |
| `MODERATION_GOOGLE_VISION_LANGUAGE_HINT` | `ko` |
| `MODERATION_GOOGLE_VISION_TIMEOUT_SECONDS` | `5.0` |

## Google Cloud Vision OCR 사용

Google Cloud Console에서 Cloud Vision API를 활성화하고 API key를 만든 뒤 아래처럼 설정합니다.

```bash
export MODERATION_OCR_ENABLED=true
export MODERATION_GOOGLE_VISION_API_KEY="your-google-api-key"
```

또는 `moderation-server/.env` 파일에 같은 값을 저장할 수 있습니다. `.env` 파일은 커밋하지 않습니다.

Spring Boot 연동용 `POST /check`는 이미지 URL을 Google Vision `imageUri`로 전달합니다. 이 URL은 Google Vision이 접근할 수 있는 공개 URL이어야 합니다. 로컬 MinIO의 `localhost:9000` URL은 Google Cloud에서 접근할 수 없으므로 OCR 테스트에는 `POST /check-file` 또는 공개 스토리지 URL이 필요합니다.
