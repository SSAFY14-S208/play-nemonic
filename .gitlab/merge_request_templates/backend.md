## 📄 요약 (Summary)
<!-- 이번 MR에서 어떤 백엔드 작업을 했는지 간단하게 설명해주세요. -->
-

<br>

## 🔗 관련 이슈 (Related Issue)
<!-- 본 MR과 관련된 Jira 이슈 번호를 적어주세요. -->
- Closes S14P31S208-

<br>

## ✨ 주요 변경 사항 (Key Changes)
<!-- 리뷰어가 중점적으로 봐야 할 변경 사항을 목록으로 작성해주세요. -->
-

<br>

## ✅ 백엔드 체크리스트 (Backend Checklist)
<!-- 완료한 항목은 체크해주세요. 해당 없으면 N/A를 적어주세요. -->
- [ ] 관련 기획서(`backend/docs/product-spec/`)를 확인했습니다.
- [ ] 패키지 구조가 `backend/docs/backend-architecture.md`를 따릅니다.
- [ ] Controller에 비즈니스 로직을 넣지 않았습니다.
- [ ] Entity를 API 응답으로 직접 반환하지 않았습니다.
- [ ] Request DTO에 필요한 validation을 추가했습니다.
- [ ] 테스트를 추가하거나 기존 테스트를 갱신했습니다.
- [ ] API 변경 시 `backend/docs/api/*.http`를 갱신했습니다.
- [ ] DB 변경 시 Flyway migration을 추가했습니다.
- [ ] 비밀값, `.env`, 로컬 캐시를 커밋하지 않았습니다.

<br>

## 🧪 검증 결과 (Verification)
<!-- 실행한 명령과 결과를 적어주세요. -->
- [ ] `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1`
- [ ] `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1`

<br>

## 📸 스크린샷 / 응답 예시 (Screenshots or API Examples)
<!-- UI 변경, API 응답, HTTP Client 결과 등이 있다면 첨부해주세요. 없으면 N/A -->
N/A

<br>

## 🙏 리뷰어에게 (To the Reviewer)
<!-- 특별히 봐야 할 부분, 남은 리스크, 테스트 시 참고할 점을 적어주세요. -->
-
