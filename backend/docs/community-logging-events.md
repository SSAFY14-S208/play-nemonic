# 커뮤니티 캔버스 로그 이벤트 목록

이 문서는 현재 백엔드 코드에 실제로 삽입되어 있는 커뮤니티 캔버스 관련 구조화 로그를 정리한다. 운영 백오피스 통계, 관리자 감사 추적, 신고/숨김 판단 근거 확인, 장애 원인 분석에서 어떤 이벤트를 보면 되는지 빠르게 확인하기 위한 문서다.

## 공통 로그 포맷

커뮤니티 로그는 `StructuredEventLogger`를 통해 JSON 형태로 출력된다. 사용자 API 로그는 `CommunityMemoEventLogger`, 관리자 조작 로그는 `AdminAuditLogger`, 파일 업로드 로그는 files API의 COMMUNITY 목적 처리에서 남긴다.

| 필드 | 설명 |
| --- | --- |
| `@timestamp` | 로그가 생성된 시각이다. ISO offset date-time 형식으로 기록된다. |
| `level` | `INFO` 또는 `WARN`이다. 정상 비즈니스/감사 이벤트는 `INFO`, 보안성 접근이나 장애 추적 이벤트는 주로 `WARN`이다. |
| `service` | 로그를 발생시킨 서비스 구분이다. 일반 API는 `backend-api`, 백오피스 감사 로그는 `backoffice-api`를 사용한다. |
| `log_type` | `business_event`, `audit_event`, `system_event` 중 하나다. |
| `event_name` | 아래 표의 이벤트 이름이다. |
| `message` | 이벤트 이름 또는 시스템 경고 메시지다. |
| `content_type` | 일반 커뮤니티 메모 이벤트는 주로 `community_memo`, 커뮤니티 파일 이벤트는 `community_file`을 사용한다. |
| `actor_id` | 이벤트 주체 식별자다. 사용자 이벤트에서는 익명 사용자 UUID, 관리자 감사 이벤트에서는 관리자 ID가 들어간다. |
| `metadata` | 이벤트별 상세 필드다. 통계와 검색은 주로 이 객체를 사용한다. |
| `error` | 예외 객체를 함께 넘긴 시스템 이벤트에만 들어간다. `type`, `message`를 가진다. |

출력 대상 로거는 다음과 같다.

| 호출 | logger | service | log_type | 용도 |
| --- | --- | --- | --- | --- |
| `apiBusiness` | `logs.api` | `backend-api` | `business_event` | 커뮤니티 사용자 API와 파일 API의 정상 비즈니스 이벤트 |
| `apiWarn` | `logs.api` | `backend-api` | `system_event` | 커뮤니티 API 처리 중 보안성 접근, 장애, 성능 경고 이벤트 |
| `audit` | `logs.audit` | `backoffice-api` | `audit_event` | 관리자 백오피스 조회/상태 변경 감사 이벤트 |
| `auditWarn` | `logs.audit` | `backoffice-api` | `audit_event` | 관리자 인증 실패, 권한 실패, 잘못된 백오피스 검색 조건 같은 감사 경고 이벤트 |

## 검색 기준으로 자주 쓰는 metadata 키

로그 저장소에서 커뮤니티 이벤트를 검색할 때는 아래 키를 우선 인덱싱 대상으로 본다.

| 키 | 설명 |
| --- | --- |
| `actor_type` | 이벤트 주체 유형이다. `user`, `anonymous`, `admin`, `system` 등이 들어간다. |
| `user_uuid` | 일반 사용자 익명 UUID다. |
| `admin_id` | 관리자 ID다. |
| `memo_id` | 커뮤니티 메모 UUID다. |
| `report_id` | 커뮤니티 메모 신고 ID다. |
| `source_type` | `DIRECT` 또는 `GALLERY`다. |
| `artifact_id` | GALLERY 출처 artifact UUID다. DIRECT는 `null`일 수 있다. |
| `hidden_reason` | 숨김 사유다. `report_threshold`, `ai_moderation`, `admin_hidden` 등이 들어간다. |
| `deleted_reason` | soft delete 사유다. `user_delete`, `expired` 등이 들어간다. |
| `moderation_status` | 메모 모더레이션 상태다. `pending`, `allowed`, `blocked` 등이 들어간다. |
| `report_reason` 또는 `reason` | 신고 사유다. 코드 위치에 따라 `reason`으로 기록되는 이벤트도 있다. |
| `result` | 관리자 감사 이벤트의 결과다. `requested`, `success`, `failure` 등이 들어간다. |
| `reason_code` | 실패/거절 사유 코드다. `bad_request`, `forbidden`, `not_found`, `hidden`, `deleted` 등이 들어간다. |
| `trace_id` | 요청 추적 ID다. 관리자 감사 이벤트와 일부 공통 로그에서 함께 본다. |
| `duration_ms` | API 또는 외부 연동 처리 시간이다. |

## 사용자 진입/조회 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_list_viewed` | `GET /api/v1/community/memos` 목록 조회가 성공했을 때 | `actor_type`, `user_uuid`, `viewer_user_uuid_present`, `item_count`, `duration_ms`, `status` | 커뮤니티 벽 목록 조회량과 응답 아이템 수를 집계한다. 익명 사용자 UUID 헤더는 optional이라 없으면 `actor_type=anonymous`로 기록된다. |
| `community_memo_detail_viewed` | `GET /api/v1/community/memos/{memoId}` 상세 조회가 성공했을 때 | `memo_id`, `user_uuid`, `owned_by_me`, `source_type`, `report_count`, `duration_ms`, `status` | 상세 패널 조회량, 본인 메모 여부, 출처 유형을 확인한다. |
| `community_memo_detail_not_found` | 상세 조회 대상이 없거나 삭제/숨김 상태라 조회할 수 없을 때 | `memo_id`, `user_uuid`, `reason_code`, `duration_ms`, `status` | 숨김/삭제/없는 메모 접근 흐름을 추적한다. `reason_code`는 `not_found`, `hidden`, `deleted`, `not_visible` 중 하나로 볼 수 있다. |

## 메모 작성/게시 퍼널 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_presign_requested` | files API에서 `purpose=COMMUNITY` presign 생성이 성공했을 때 | `file_id`, `user_uuid`, `object_key`, `content_type`, `byte_size`, `purpose`, `status` | 커뮤니티 게시용 원본/썸네일 파일 업로드 시작량을 본다. |
| `community_memo_upload_confirmed` | files API에서 `purpose=COMMUNITY` 파일 confirm이 성공했을 때 | `file_id`, `user_uuid`, `object_key`, `content_type`, `byte_size`, `purpose`, `status` | MinIO 업로드 완료 후 백엔드 파일 상태가 `UPLOADED`로 확정된 시점을 본다. |
| `community_memo_upload_deleted` | COMMUNITY 목적 파일이 삭제 처리됐을 때 | `file_id`, `user_uuid`, `object_key`, `content_type`, `byte_size`, `purpose`, `status` | 게시 전 파일 정리나 삭제 흐름을 추적한다. |
| `community_memo_create_requested` | `POST /api/v1/community/memos` 생성 요청의 기본 검증이 진행될 때 | `user_uuid`, `source_type`, `original_file_id`, `thumbnail_file_id`, `source_gallery_id`, `source_artifact_id` | 최종 스냅샷 게시 요청 진입점을 본다. |
| `community_memo_create_validation_failed` | 생성 요청 검증 또는 생성 직후 조회가 실패했을 때 | `user_uuid`, `source_type`, `original_file_id`, `thumbnail_file_id`, `source_gallery_id`, `reason_code`, `message` | 파일 없음, 파일 소유자 불일치, purpose/status 오류, sourceGallery 오류 같은 생성 실패 원인을 집계한다. |
| `community_memo_created` | 메모 insert, 모더레이션 통과, FIFO 정리까지 끝난 뒤 생성 응답 직전 | `memo_id`, `user_uuid`, `source_type`, `artifact_id`, `original_file_id`, `thumbnail_file_id`, `body_image_object_key`, `thumbnail_image_object_key`, `report_count`, `moderation_status` | 실제 커뮤니티 벽에 게시된 메모 수와 게시 이미지 object key를 추적한다. |

## 모더레이션 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_moderation_requested` | FastAPI `/check` 호출 직전 | `user_uuid`, `source_type`, `source_artifact_id`, `original_file_id`, `thumbnail_file_id`, `original_image_url`, `thumbnail_url`, `client_text_length`, `client_text_preview` | 게시 전 동기 모더레이션 호출 입력을 추적한다. |
| `community_memo_moderation_allowed` | FastAPI가 게시 허용 응답을 반환했을 때 | `user_uuid`, `source_type`, `allowed`, `client_text_length`, `client_text_preview`, `ocr_text_length`, `ocr_text_preview`, `categories`, `latency_ms`, `checked_at` | 모더레이션 통과율, OCR 결과, 모델 응답 시간을 확인한다. |
| `community_memo_moderation_blocked` | FastAPI가 게시 차단 응답을 반환했을 때 | `user_uuid`, `source_type`, `allowed`, `client_text_length`, `client_text_preview`, `ocr_text_length`, `ocr_text_preview`, `categories`, `latency_ms`, `checked_at` | 유해성 감지로 게시가 차단된 케이스를 집계한다. |
| `community_memo_moderation_failed` | FastAPI 호출 실패, timeout, 5xx, 응답 파싱 실패 등으로 fail-closed 처리할 때 | `user_uuid`, `source_type`, `client_text_length`, `latency_ms`, `fail_closed` | 모더레이션 장애로 메모 insert가 중단된 상황을 추적한다. |
| `community_memo_moderation_slow` | 모더레이션 응답 시간이 임계값을 초과했을 때 | `user_uuid`, `source_type`, `latency_ms`, `threshold_ms` | FastAPI 지연 감지와 알림 지표에 사용한다. 현재 임계값은 30초다. |

## FIFO 자동 정리 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_fifo_checked` | 메모 생성 성공 후 visible 메모 수를 확인할 때 | `new_memo_id`, `visible_memo_count`, `max_visible_memo_count`, `overflow_count` | 생성 후 FIFO 정리 필요 여부를 판단한 기록이다. |
| `community_memo_fifo_skipped` | visible 메모가 50개 이하라 정리를 건너뛰었을 때 | `new_memo_id`, `visible_memo_count`, `limit` | 정상적으로 정리가 필요 없었던 케이스를 확인한다. |
| `community_memo_fifo_expired` | 오래된 visible 메모를 `expired`로 soft delete 했을 때 | `new_memo_id`, `requested_expire_count`, `expired_count`, `expired_memo_ids`, `deleted_reason` | 시스템이 자동으로 노출 메모를 정리한 내역이다. 사용자 삭제와 구분해야 한다. |

## 메모 위치 수정 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_layout_update_requested` | `PATCH /api/v1/community/memos/{memoId}` 위치 수정 요청 검증 후 | `memo_id`, `user_uuid`, `position_x`, `position_y`, `z_index`, `rotation_deg` | 사용자가 메모 위치 저장을 요청한 시점을 기록한다. 드래그 중 실시간 이동이 아니라 저장 API 기준이다. |
| `community_memo_layout_updated` | 위치 수정 SQL이 성공하고 상세 응답 재조회가 끝났을 때 | `memo_id`, `user_uuid`, `source_type`, `before_position_x`, `before_position_y`, `before_z_index`, `before_rotation_deg`, `after_position_x`, `after_position_y`, `after_z_index`, `after_rotation_deg` | 배치 변경 전후 값을 비교할 수 있다. |
| `community_memo_layout_update_denied` | 타인 메모 위치 수정을 시도했을 때 | `memo_id`, `user_uuid`, `memo_owner_uuid`, `reason` | 소유권 위반 시도다. `community_ownership_violation` 경고 로그와 함께 볼 수 있다. |
| `community_memo_layout_update_failed` | 위치 수정 요청이 검증 실패, hidden/deleted/not found 등으로 실패했을 때 | `memo_id`, `user_uuid`, `reason_code`, `visibility_reason_code`, `message` | 수정 실패 원인을 추적한다. |

## 사용자 메모 삭제 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_delete_requested` | `DELETE /api/v1/community/memos/{memoId}` 삭제 요청 진입 후 | `memo_id`, `user_uuid` | 사용자가 본인 메모 삭제를 요청한 시점을 기록한다. |
| `community_memo_user_deleted` | 본인 visible 메모가 `user_delete`로 soft delete 되었을 때 | `memo_id`, `user_uuid`, `source_type`, `deleted_reason`, `deleted_at` | 사용자 삭제 성공 로그다. MinIO 파일, file_upload, artifact, gallery는 삭제하지 않는다. |
| `community_memo_delete_denied` | 타인 메모 삭제를 시도했을 때 | `memo_id`, `user_uuid`, `memo_owner_uuid`, `reason` | 삭제 권한 위반 시도다. |
| `community_memo_delete_failed` | 삭제 요청이 hidden/deleted/not found 등으로 실패했을 때 | `memo_id`, `user_uuid`, `reason_code`, `visibility_reason_code`, `message` | 삭제 실패 원인을 추적한다. |

## 신고 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_memo_report_requested` | `POST /api/v1/community/memos/{memoId}/reports` 신고 요청 검증 후 | `memo_id`, `user_uuid`, `reason`, `reason_detail_present` | 신고 요청 진입 시점이다. |
| `community_memo_report_created` | 신고 row 생성과 `report_count` 증가가 성공했을 때 | `report_id`, `memo_id`, `user_uuid`, `memo_owner_uuid`, `reason`, `reason_detail`, `reason_detail_present`, `report_count`, `hidden` | 신고 성공 로그다. 신고 상세 사유가 있으면 함께 남는다. |
| `community_memo_report_rejected` | 본인 메모 신고, 중복 신고, hidden/deleted 메모 신고 등으로 거절됐을 때 | `memo_id`, `user_uuid`, `reason`, `reject_reason`, `reason_code`, `visibility_reason_code`, `message` | 신고 거절 사유를 집계한다. |
| `community_memo_report_threshold_reached` | 신고 수가 자동 숨김 임계값에 도달했을 때 | `memo_id`, `user_uuid`, `report_count`, `threshold`, `reason` | 신고 5회 도달 시점이다. |
| `community_memo_auto_hidden_by_report` | 신고 임계값 도달로 메모가 자동 숨김 처리됐을 때 | `memo_id`, `user_uuid`, `report_count`, `threshold`, `hidden_reason`, `hidden_at` | `hidden_reason=report_threshold` 자동 숨김 처리 로그다. |

## 관리자 백오피스 조회 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `admin_community_memo_list_viewed` | 관리자가 커뮤니티 메모 목록을 조회했을 때 | `admin_id`, `admin_role`, `hidden`, `moderation_status`, `source_type`, `reported`, `keyword_present`, `page`, `size`, `total_elements`, `trace_id` | 운영자가 어떤 조건으로 목록을 조회했는지 감사 추적한다. |
| `admin_community_memo_detail_viewed` | 관리자가 커뮤니티 메모 상세를 조회했을 때 | `admin_id`, `memo_id`, `hidden`, `report_count`, `trace_id` | 민감한 운영 상세 조회 이력을 남긴다. |
| `admin_community_memo_reports_viewed` | 관리자가 특정 메모의 신고 내역을 조회했을 때 | `admin_id`, `memo_id`, `reason`, `page`, `size`, `total_elements`, `trace_id` | 신고 내역 열람 이력이다. |
| `admin_community_memo_search_failed` | 관리자 목록/상세/신고 내역 조회 조건이 잘못됐을 때 | `admin_id`, `operation`, `target_id`, `reason_code`, `trace_id` | 잘못된 필터, 잘못된 UUID, pagination 오류 같은 백오피스 조회 실패를 추적한다. |

## 관리자 상태 변경 감사 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `admin_community_memo_hide_requested` | 관리자가 메모 숨김을 요청했을 때 | `admin_id`, `memo_id`, `input_reason`, `action`, `result`, `trace_id` | 수동 숨김 요청 자체를 먼저 남긴다. |
| `admin_community_memo_hidden` | visible 메모가 관리자 수동 숨김 처리됐을 때 | `admin_id`, `memo_id`, `input_reason`, `hidden_reason`, `hidden_reason_detail`, `state_changed`, `before_is_hidden`, `after_is_hidden`, `before_hidden_reason`, `after_hidden_reason`, `before_hidden_at`, `after_hidden_at`, `before_reviewed_by`, `after_reviewed_by`, `before_reviewed_at`, `after_reviewed_at`, `trace_id` | 수동 숨김 성공 감사 로그다. `hidden_reason=admin_hidden`이고 입력 사유는 상세 사유로 남는다. |
| `admin_community_memo_hide_noop` | 이미 hidden인 메모에 숨김 요청을 보냈을 때 | `admin_id`, `memo_id`, `input_reason`, `hidden_reason_detail`, `state_changed=false`, before/after 상태 필드, `trace_id` | 멱등 처리된 숨김 요청이다. 상태는 바뀌지 않는다. |
| `admin_community_memo_restore_requested` | 관리자가 숨김 복구를 요청했을 때 | `admin_id`, `memo_id`, `input_reason`, `action`, `result`, `trace_id` | 복구 요청 자체를 먼저 남긴다. |
| `admin_community_memo_restored` | hidden 메모가 visible로 복구됐을 때 | `admin_id`, `memo_id`, `restore_reason_detail`, `state_changed`, before/after 상태 필드, `trace_id` | 복구 성공 감사 로그다. `hidden_reason`, `hidden_at`은 null로 돌아간다. |
| `admin_community_memo_restore_noop` | 이미 visible인 메모에 복구 요청을 보냈을 때 | `admin_id`, `memo_id`, `restore_reason_detail`, `state_changed=false`, before/after 상태 필드, `trace_id` | 멱등 처리된 복구 요청이다. 상태는 바뀌지 않는다. |

## 관리자 인증/권한 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `admin_login_success` | 관리자 로그인이 성공했을 때 | `admin_id`, `admin_role`, `actor_ip`, `target_id`, `action`, `result`, `trace_id` | 백오피스 접근 시작 이력이다. |
| `admin_login_failed` | 관리자 로그인이 실패했을 때 | `admin_id` 또는 `unknown`, `admin_role` 또는 `unknown`, `actor_ip`, `target_id`, `action`, `result`, `trace_id` | 비밀번호 오류, 삭제 계정 등 로그인 실패 이력이다. |
| `admin_logout` | 관리자 로그아웃이 성공했을 때 | `admin_id`, `admin_role`, `actor_ip`, `target_id`, `action`, `result`, `trace_id` | 백오피스 세션 종료 이력이다. |
| `admin_token_invalid` | 관리자 JWT가 없거나 잘못됐거나 만료/블랙리스트 처리됐을 때 | `path`, `method`, `reason_code`, `trace_id` | 인증 실패 추적에 사용한다. |
| `admin_access_denied` | 인증은 됐지만 접근이 거부됐을 때 | `path`, `method`, `reason_code`, `trace_id` | 권한 부족 접근을 추적한다. |
| `admin_forbidden` | 관리자 API에서 인가 실패가 발생했을 때 | `path`, `method`, `reason_code`, `trace_id` | 백오피스 권한 실패를 감사 로그로 본다. |

## 보안/비정상 접근 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | 설명 |
| --- | --- | --- | --- |
| `community_invalid_uuid_repeated` | 커뮤니티 API에 잘못된 `Anonymous-User-UUID` 헤더가 들어왔을 때 | `path`, `method`, `reason_code`, `header_name`, `attempt_count`, `repeated` | 동일 원격 주소 기준 잘못된 UUID 반복 여부를 추적한다. |
| `community_missing_user_header` | 익명 사용자 헤더가 필수인 생성/수정/삭제/신고 API에서 헤더가 누락됐을 때 | `path`, `method`, `reason_code`, `header_name` | 인증성 헤더 누락 접근이다. |
| `community_user_not_found` | UUID 형식은 맞지만 존재하지 않는 사용자로 상태 변경 API를 호출했을 때 | `user_uuid`, `reason_code` | 비정상 사용자 식별자를 추적한다. |
| `community_ownership_violation` | 타인 메모 수정/삭제 같은 소유권 위반이 발생했을 때 | `memo_id`, `user_uuid`, `reason_code`, `message` | 본인 리소스가 아닌 메모 조작 시도다. |
| `community_duplicate_report_attempt` | 같은 사용자가 같은 메모를 중복 신고했을 때 | `memo_id`, `user_uuid`, `reason_code`, `message` | 중복 신고 시도다. |
| `community_hidden_memo_access_attempt` | 숨김 메모에 일반 사용자 API로 접근했을 때 | `memo_id`, `user_uuid`, `reason_code` 또는 `visibility_reason_code` | hidden 메모는 일반 API에서 404로 감추지만 운영 로그에는 시도가 남는다. |
| `community_deleted_memo_access_attempt` | 삭제 메모에 일반 사용자 API로 접근했을 때 | `memo_id`, `user_uuid`, `reason_code` 또는 `visibility_reason_code` | deleted 메모는 일반 API에서 404로 감추지만 운영 로그에는 시도가 남는다. |
| `community_file_ownership_violation` | 메모 생성 시 다른 사용자의 file_upload를 사용하려고 했을 때 | `user_uuid`, `original_file_id`, `thumbnail_file_id`, `reason_code` | 최종 스냅샷 파일 소유자 불일치 시도다. |

## 장애/성능 이벤트

| 이벤트 | 발생 시점 | 주요 metadata | error 필드 | 설명 |
| --- | --- | --- | --- | --- |
| `community_api_slow_request` | 커뮤니티 사용자/관리자 API 처리 시간이 임계값을 초과했을 때 | `path`, `method`, `status`, `duration_ms`, `threshold_ms` | 있을 수 있음 | 느린 API 요청을 찾는다. 현재 임계값은 1초다. |
| `community_repository_query_failed` | 커뮤니티 사용자 API 처리 중 DB 접근 예외가 발생했을 때 | `path`, `method`, `exception_type` 등 | 있음 | 일반 커뮤니티 API의 DB 장애 추적 이벤트다. |
| `community_admin_query_failed` | 관리자 커뮤니티 API 처리 중 DB 접근 예외가 발생했을 때 | `path`, `method`, `exception_type` 등 | 있음 | 관리자 목록/상세/신고 내역 조회 장애 추적 이벤트다. |
| `community_file_url_resolve_failed` | object key를 public URL로 변환하지 못했을 때 | `memo_id`, `image_role`, `object_key` | 없음 | 메모 원본/썸네일 URL이 응답에서 비어 보일 수 있는 원인을 찾는다. |
| `community_decoration_parse_failed` | DB에 저장된 decoration JSON을 파싱하지 못했을 때 | `decoration_preview` | 있음 | 깨진 decoration 때문에 API를 실패시키지 않고 `{}` fallback 한 상황이다. |

## 현재 백엔드 코드에서 직접 찍지 않는 로그

아래 이벤트는 커뮤니티 흐름상 필요하지만, 백엔드 API만으로는 정확한 발생 시점을 알 수 없다. 프론트 이벤트 수집 API나 클라이언트 로그 전송이 필요하다.

| 이벤트 | 현재 상태 | 이유 |
| --- | --- | --- |
| `community_canvas_opened` | 프론트 수집 필요 | 커뮤니티 캔버스 화면 진입은 서버 API 호출 없이도 발생할 수 있다. |
| `community_memo_compose_started` | 프론트 수집 필요 | 사용자가 빈 캔버스, GALLERY, PHOTO 중 어떤 작성 흐름을 시작했는지는 프론트 작성 UI에서 가장 정확히 안다. |
| `community_memo_snapshot_exported` | 프론트 수집 필요 | 최종 원본/썸네일 이미지 export 완료와 이미지 크기는 브라우저에서 발생하는 이벤트다. |

## 운영 시 읽는 법

정상 DIRECT 게시 흐름은 대체로 `community_memo_presign_requested` -> `community_memo_upload_confirmed` -> `community_memo_create_requested` -> `community_memo_moderation_requested` -> `community_memo_moderation_allowed` -> `community_memo_created` 순서로 이어진다. GALLERY 게시도 표시 이미지는 artifact가 아니라 새로 업로드한 snapshot이므로 같은 파일/모더레이션 흐름을 탄다. GALLERY 여부는 `source_type=GALLERY`, `artifact_id` 또는 `source_artifact_id`로 확인한다.

게시 실패를 볼 때는 `community_memo_create_validation_failed`, `community_memo_moderation_blocked`, `community_memo_moderation_failed`를 먼저 본다. 파일 소유권 문제는 `community_file_ownership_violation`, FastAPI 장애는 `community_memo_moderation_failed.fail_closed=true`, 유해성 차단은 `community_memo_moderation_blocked.allowed=false`로 좁혀 보면 된다.

신고와 자동 숨김은 `community_memo_report_created`의 `report_count`를 따라가다가, 5회 도달 시 `community_memo_report_threshold_reached`와 `community_memo_auto_hidden_by_report`가 이어지는지 확인한다. 신고 후 일반 사용자 목록/상세에서 사라졌는지는 `community_hidden_memo_access_attempt`와 관리자 `admin_community_memo_detail_viewed`를 함께 보면 된다.

관리자 수동 숨김/복구는 요청 이벤트와 결과 이벤트를 쌍으로 본다. 숨김은 `admin_community_memo_hide_requested` -> `admin_community_memo_hidden`, 복구는 `admin_community_memo_restore_requested` -> `admin_community_memo_restored` 순서다. 이미 같은 상태라 변경이 없으면 각각 `admin_community_memo_hide_noop`, `admin_community_memo_restore_noop`가 남는다. 상태 변경 전후는 `before_*`, `after_*` metadata로 비교한다.

FIFO 자동 정리는 사용자 삭제와 다르게 `deleted_reason=expired`로 남는다. 새 메모 생성 직후 `community_memo_fifo_checked`를 보고, `overflow_count`가 0보다 크면 `community_memo_fifo_expired.expired_memo_ids`로 어떤 메모가 정리됐는지 확인한다.
