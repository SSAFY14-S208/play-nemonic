# 릴레이 드로잉 로그 이벤트 목록

이 문서는 현재 백엔드 코드에 실제로 삽입되어 있는 릴레이 드로잉 관련 구조화 로그를 정리한다. 운영 백오피스 통계, 관리자 감사 추적, 장애 원인 분석에서 어떤 이벤트를 보면 되는지 빠르게 확인하기 위한 문서다.

## 공통 로그 포맷

릴레이 로그는 `RelayRoomEventLogger`를 통해 JSON 형태로 출력된다. 모든 이벤트는 아래 공통 필드를 가진다.

| 필드 | 설명 |
| --- | --- |
| `@timestamp` | 로그가 생성된 시각이다. ISO offset date-time 형식으로 기록된다. |
| `level` | `INFO` 또는 `WARN`이다. 정상 비즈니스/감사 이벤트는 `INFO`, 장애 추적 이벤트는 `WARN`이다. |
| `service` | 로그를 발생시킨 서비스 구분이다. `backend-api`, `websocket-server`, `backoffice-api` 중 하나가 들어간다. |
| `log_type` | `business_event`, `audit_event`, `system_event` 중 하나다. |
| `event_name` | 아래 표의 이벤트 이름이다. |
| `message` | 이벤트 이름 또는 시스템 경고 메시지다. |
| `metadata` | 이벤트별 상세 필드다. 통계와 검색은 주로 이 객체를 사용한다. |
| `error` | 예외 객체를 함께 넘긴 시스템 이벤트에만 들어간다. `type`, `message`를 가진다. |

출력 대상 로거는 다음과 같다.

| 호출 | logger | service | log_type | 용도 |
| --- | --- | --- | --- | --- |
| `apiBusiness` | `logs.api` | `backend-api` | `business_event` | REST API 또는 스케줄러가 확정한 정상 비즈니스 이벤트 |
| `websocketBusiness` | `logs.websocket` | `websocket-server` | `business_event` | WebSocket 연결/방송/실시간 진행 이벤트 |
| `apiWarn` | `logs.api` | `backend-api` | `system_event` | REST API 또는 스케줄러 처리 중 경고/장애 추적 이벤트 |
| `websocketWarn` | `logs.websocket` | `websocket-server` | `system_event` | WebSocket 처리 중 경고/장애 추적 이벤트 |
| `audit` | `logs.audit` | `backoffice-api` | `audit_event` | 관리자 조작에 대한 감사 이벤트 |

`room_id`는 별도의 DB PK가 아니라 릴레이 방의 `roomCode`를 의미한다. `part`는 `FACE`, `BODY`, `LEGS` 중 하나다. 거절 이벤트에서 요청이 UUID 형식 오류, 방 코드 형식 오류, 방 없음처럼 방 상태를 확정할 수 없는 단계에서 실패하면 `room_status`, `current_part`, `participant_count` 같은 상태 필드는 `null`일 수 있다.

## 백오피스 통계용 비즈니스 이벤트

### 방 생성과 대기방

| 이벤트 | 발생 시점 | metadata 필드 | 설명 |
| --- | --- | --- | --- |
| `relay_room_created` | 릴레이 방 생성이 Redis 저장까지 성공한 직후 | `room_id`, `host_uuid`, `time_limit_seconds`, `max_participants` | 방 생성 수, 방장 기준 생성량, 기본 제한 시간 사용 현황을 집계할 수 있다. |
| `relay_room_settings_changed` | 대기방에서 방장이 제한 시간을 변경하고 Redis CAS 저장이 성공한 직후 | `room_id`, `actor_uuid`, `before`, `after` | 설정 변경 이력을 본다. 현재 `before`/`after`에는 `time_limit_seconds`, `max_participants`가 들어간다. |
| `relay_participant_joined` | 참여자가 대기방에 새로 들어오거나 재입장 처리가 성공한 직후 | `room_id`, `uuid`, `participant_count`, `join_order`, `reconnect_attempt` | 대기방 유입, 재입장 비율, 인원 증가 흐름을 집계한다. `reconnect_attempt=true`면 기존 참여자의 재입장 시도다. |
| `relay_participant_left` | 대기방 참여자가 자진 퇴장하고 Redis 저장이 성공한 직후 | `room_id`, `uuid`, `room_status`, `participant_count`, `left_at` | 대기방 이탈률과 마지막 참여자 퇴장으로 방이 닫혔는지 확인한다. |
| `relay_participant_kicked` | 방장이 대기방 참여자를 강퇴하고 Redis 저장이 성공한 직후 | `room_id`, `host_uuid`, `kicked_uuid`, `room_status`, `participant_count` | 강퇴 빈도와 방장 조작 이력을 통계로 볼 수 있다. |
| `relay_host_changed` | 방장 자진 퇴장 또는 게임 중 방장 이탈 확정으로 방장이 위임된 직후 | `room_id`, `previous_host_uuid`, `new_host_uuid`, `reason` | 방장 교체 흐름을 추적한다. 현재 `reason`은 `host_left`, `host_dropped`가 사용된다. |

### WebSocket 연결 상태

| 이벤트 | 발생 시점 | metadata 필드 | 설명 |
| --- | --- | --- | --- |
| `relay_ws_connected` | STOMP CONNECT 검증, Redis 연결 상태 갱신, 세션 등록이 성공한 직후 | `room_id`, `uuid`, `session_id`, `participant_count`, `is_host` | 실제 WebSocket 연결 성공 기준 참여자 수를 본다. REST 입장만 하고 WebSocket에 붙지 않은 사용자는 여기에 잡히지 않는다. |
| `relay_ws_reconnected` | 게임 중 끊겼던 참여자가 재연결 유예 시간 안에 다시 연결된 직후 | `room_id`, `uuid`, `old_disconnected_at`, `session_id`, `room_status`, `current_part` | 네트워크 이탈 후 복구율을 볼 수 있다. `old_disconnected_at`으로 끊긴 뒤 복귀까지 걸린 시간을 계산할 수 있다. |
| `relay_ws_disconnected` | WebSocket DISCONNECT 이벤트를 받아 Redis 연결 해제 상태 저장이 성공한 직후 | `room_id`, `uuid`, `session_id`, `disconnect_reason`, `room_status`, `current_part` | 연결 해제 시점의 방 상태와 파트를 추적한다. 게임 중 끊김과 대기방 끊김을 구분할 수 있다. |
| `relay_ws_connection_rejected` | STOMP CONNECT 검증 또는 Redis 상태 갱신이 실패해 연결을 거부할 때 | `room_id`, `uuid`, `session_id`, `reject_reason`, `room_status`, `exception_type` | 닫힌 방, 강퇴 사용자, 이탈 확정 사용자, 잘못된 요청 등 연결 거부 사유를 집계한다. |
| `relay_duplicate_session_closed` | 동일 사용자가 같은 릴레이 방에 중복 접속해 기존 세션을 닫을 때 | `room_id`, `uuid`, `old_session_id`, `new_session_id` | 중복 접속 상황을 추적한다. 새 세션을 살리고 기존 세션을 닫는 흐름이다. |
| `relay_room_state_snapshot_sent` | 연결 또는 재연결 성공 후 현재 방 상태를 사용자에게 전송한 직후 | `room_id`, `uuid`, `room_status`, `current_part`, `participant_count` | 클라이언트가 최신 방 스냅샷을 받은 시점을 확인한다. 연결 성공 이벤트와 함께 보면 초기 동기화 누락을 추적할 수 있다. |

### 게임 시작과 파트 진행

| 이벤트 | 발생 시점 | metadata 필드 | 설명 |
| --- | --- | --- | --- |
| `relay_start_rejected` | 방장이 게임 시작을 요청했지만 검증에 실패한 때 | `room_id`, `host_uuid`, `reject_reason`, `participant_count`, `room_status`, `exception_type` | 인원 부족, 미연결 참여자 존재, 이미 시작/종료된 방 등 시작 실패 사유를 집계한다. |
| `relay_game_started` | 방장이 게임 시작에 성공하고 FACE 배정이 Redis에 저장된 직후 | `room_id`, `host_uuid`, `participant_count`, `assignment_count`, `time_limit_seconds` | 실제 게임 시작 수, 시작 인원, 생성된 배정 수를 집계한다. |
| `relay_part_started` | FACE/BODY/LEGS 파트가 시작된 직후 | `room_id`, `part`, `previous_part`, `participant_count`, `part_deadline_at` | 각 파트 시작 시각과 마감 시각을 추적한다. 게임 시작 시 FACE는 `previous_part=null`이다. |
| `relay_part_time_up` | 현재 파트의 `part_deadline_at`이 지나고 자동 제출 유예 구간에 진입했음을 WebSocket으로 알린 직후 | `room_id`, `part`, `pending_count`, `pending_user_uuids`, `part_deadline_at`, `submit_grace_deadline_at` | 프론트가 deadline 종료를 인지하도록 보내는 이벤트다. 아직 제출하지 않은 사용자 목록도 함께 들어간다. |
| `relay_drawing_submitted` | 사용자의 현재 파트 제출이 파일 업로드와 Redis 저장까지 성공한 직후 | `room_id`, `uuid`, `canvas_index`, `part`, `submitted_at`, `current_part_completed` | 정상 제출 수와 파트 완료 여부를 집계한다. `current_part_completed=true`면 해당 제출로 현재 파트가 모두 완료된 것이다. |
| `relay_submission_rejected` | 제출 요청이 검증 또는 처리 중 실패한 때 | `room_id`, `uuid`, `canvas_index`, `part`, `reject_reason`, `room_status`, `current_part`, `exception_type` | 잘못된 파트, 이미 제출됨, 참여자가 아님, 방 상태 오류 등 제출 실패 원인을 볼 수 있다. |
| `relay_part_auto_submitted` | timeout 또는 참여자 이탈 확정으로 현재 파트가 빈 제출 처리된 직후 | `room_id`, `uuid`, `canvas_index`, `part`, `reason`, `empty` | 자동 제출된 사용자와 사유를 집계한다. 현재 `reason`은 `timeout`, `participant_dropped`가 사용된다. |
| `relay_participant_dropped` | 게임 중 재연결 유예 시간이 지나 참여자가 이탈 확정된 직후 | `room_id`, `uuid`, `disconnected_at`, `dropped_at`, `current_part`, `auto_submitted_count` | 네트워크 이탈이 최종 이탈로 확정된 사용자를 추적한다. 현재 파트에서 자동 제출된 개수도 같이 기록된다. |
| `relay_all_parts_completed` | LEGS까지 모두 완료되어 최종화 단계로 진입한 직후 | `room_id`, `participant_count`, `assignment_count`, `completed_at` | 게임이 최종 결과물 생성 대기 상태로 넘어간 시점을 나타낸다. |

### 결과 생성, 방 종료, 임시 파일 정리

| 이벤트 | 발생 시점 | metadata 필드 | 설명 |
| --- | --- | --- | --- |
| `relay_result_created` | 최종 이미지 합성, MinIO 업로드, DB 저장, Redis `FINISHED` 전환이 성공한 직후 | `room_id`, `result_count`, `artifact_ids`, `duration_ms` | 결과물 생성 성공 수와 생성 소요 시간을 집계한다. `artifact_ids`로 DB 결과물과 연결할 수 있다. |
| `relay_room_closed` | 방이 `CLOSED` 상태로 전환된 직후 | `room_id`, `close_reason`, `room_status_before`, `participant_count` | 방 종료 사유를 집계한다. 현재 `close_reason`은 `last_participant_left`, `host_manual`, `auto_delay`, `admin_force`, `waiting_idle_timeout`, `playing_abandoned`, `waiting_empty`, `finalization_failed`가 사용된다. 자동 방치 종료는 `closed_at`과 `idle_seconds` 또는 `abandoned_seconds`를 추가하고, 비정상 empty WAITING 종료는 `participant_count=0`, 최종화 실패 종료는 `retry_count`를 추가한다. |
| `relay_temp_cleanup_completed` | 닫힌 방의 `relay/tmp/{roomCode}/` 임시 파일 삭제와 cleanup marker 저장이 성공한 직후 | `room_id`, `deleted_object_count`, `result` | 임시 파일 정리 성공 여부와 삭제 개수를 본다. 현재 성공 시 `result=success`다. |
| `relay_room_recovered_or_reconciled` | Redis에는 `connected=true`지만 같은 서버 relay WebSocket session registry에 실제 세션이 없어 Redis 상태를 보정한 직후 | `room_id`, `reason`, `before`, `after`, `reconciled_user_uuids`, `room_status`, `current_part` | 서버 재시작이나 비정상 종료 후 stale connection 상태를 찾는다. 현재 `reason=missing_ws_session`이다. 이 로그는 상태 보정 기록이며 `PARTICIPANT_DISCONNECTED` WebSocket 이벤트는 발행하지 않는다. |
| `relay_result_orphan_cleanup_completed` | 최종화 시도 중 결과 이미지 업로드 후 DB 저장이 실패해 이번 시도에서 만든 결과 object를 best-effort 삭제한 직후 | `room_id`, `artifact_ids`, `object_keys`, `deleted_object_count`, `failed_object_count`, `result` | DB에 저장되지 않은 `relay/results/**` object 정리 결과를 추적한다. `result`는 `success` 또는 `partial_failure`다. |

## 운영자 감사 로그

감사 로그는 백오피스 조작을 추적하기 위한 로그다. 사용자의 게임 진행 통계보다는 “누가 어떤 관리자 권한으로 무엇을 바꿨는지”를 보는 데 쓴다.

| 이벤트 | 발생 시점 | metadata 필드 | 설명 |
| --- | --- | --- | --- |
| `relay_room_force_close` | 백오피스에서 활성 릴레이 방을 강제 종료하고 Redis 저장이 성공한 직후 | `actor_id`, `actor_role`, `actor_ip`, `target_type`, `target_id`, `action`, `reason`, `before`, `after`, `result` | 관리자 강제 종료 감사 이벤트다. `before`에는 기존 방 상태와 참여자 수, `after`에는 `CLOSED`와 종료 시각이 들어간다. |
| `param_change` | 백오피스에서 릴레이 관련 시스템 파라미터를 변경한 직후 | `actor_id`, `actor_role`, `actor_ip`, `target_type`, `target_id`, `action`, `before`, `after`, `reason`, `result` | 릴레이 설정값 변경 감사 이벤트다. 현재 릴레이 키로 판정되는 시스템 파라미터 변경에만 찍힌다. |

## 장애 추적용 시스템 이벤트

시스템 이벤트는 통계용 성공 이벤트가 아니라 운영자가 문제를 재현하거나 원인을 좁히기 위한 경고 로그다. 예외 객체를 함께 넘긴 경우 top-level `error.type`, `error.message`가 추가된다.

| 이벤트 | 발생 시점 | metadata 필드 | error 필드 | 설명 |
| --- | --- | --- | --- | --- |
| `relay_ws_disconnect_update_failed` | WebSocket DISCONNECT를 받았지만 Redis 연결 해제 상태 저장에 실패한 때 | `room_id`, `uuid`, `session_id` | 있음 | 사용자는 실제로 끊겼지만 Redis에는 연결 상태가 남을 수 있는 위험 신호다. |
| `relay_timeout_scheduler_failed` | timeout scheduler가 만료된 PLAYING 방을 처리하다 실패한 때 | `room_id`, `part`, `operation` | 있음 | 파트 timeout 자동 제출 처리가 실패한 방을 찾는 데 사용한다. `operation=timeout`이다. |
| `relay_part_time_up_publish_failed` | deadline 종료 WebSocket 이벤트 발행 중 실패한 때 | `room_id`, `part` | 있음 | 프론트가 `PART_TIME_UP`을 못 받았을 가능성을 추적한다. |
| `relay_room_mutation_lock_busy` | 방 상태 변경용 Redis lock 획득에 실패해 이번 처리를 건너뛴 때 | `room_id`, `operation`, `lock_ttl_ms` | 없음 | 동시 제출, timeout, disconnect grace 처리 경합이 잦은지 확인한다. `operation`은 `timeout`, `disconnect_grace`, `submission` 등이 들어간다. |
| `relay_redis_cas_retry_exceeded` | Redis `saveIfUnchanged` CAS 재시도가 최대 횟수를 초과한 때 | `room_id`, `operation`, `attempt_count` | 없음 | Redis 방 상태 갱신 충돌이 반복되어 처리를 포기한 상황이다. `operation`은 `timeout`, `disconnect_grace`, `submission` 등이 들어간다. |
| `relay_minio_upload_redis_save_failed` | 제출 이미지 MinIO 업로드 후 Redis 저장이 끝내 실패한 때 | `room_id`, `uuid`, `canvas_index`, `part`, `object_key` | 없음 | 파일은 올라갔지만 방 상태 반영은 실패한 위험 상황이다. 임시 파일 정리나 재처리 판단에 중요하다. |
| `relay_disconnect_grace_scheduler_failed` | disconnect grace scheduler가 이탈 확정 후보 방 처리 중 실패한 때 | `room_id`, `uuid`, `operation` | 있음 | 재연결 유예 만료자를 확정하는 작업이 실패한 방과 후보 사용자를 찾는다. `operation=disconnect_grace`다. |
| `relay_finalization_failed` | 최종 결과물 생성 과정에서 실패한 때 | `room_id`, `stage`, `artifact_id`, `operation`, `retry_count`, `max_retry_count` | 있음 | 최종화 실패 단계별 추적 로그다. `stage`는 `process`, `compose`, `minio_upload`, `artifact_meta`, `db_save`, `redis_update` 등이 들어간다. 기본 정책은 30초 간격으로 최대 20회 재시도하고, 20회째 실패하면 방을 `CLOSED`로 전환한다. |
| `relay_result_orphan_cleanup_failed` | DB 저장 실패 후 현재 finalization attempt에서 생성한 결과 object 삭제에 실패한 때 | `room_id`, `object_keys`, `failed_object_count` | 있음 | 결과 object orphan cleanup의 부분 실패를 추적한다. 삭제 실패는 원래 finalization 예외를 대체하지 않는다. |
| `relay_temp_cleanup_failed` | CLOSED 방의 임시 파일 정리 중 실패한 때 | `room_id`, `object_key_prefix`, `failed_object_count` | 있음 | 특정 방의 임시 파일 정리가 실패한 상황이다. `failed_object_count`는 정리 대상 후보 개수다. |
| `relay_old_temp_lookup_failed` | Redis 방 상태가 사라진 오래된 임시 파일 fallback 조회가 실패한 때 | `room_id`, `object_key_prefix`, `failed_object_count` | 있음 | `relay/tmp/` 전체 fallback cleanup 대상 조회 자체가 실패한 상황이다. 현재 `room_id=null`, `failed_object_count=0`으로 기록된다. |
| `relay_old_temp_cleanup_failed` | 오래된 임시 파일 fallback 삭제가 실패한 때 | `room_id`, `object_key_prefix`, `failed_object_count` | 있음 | Redis room state가 없어도 남아 있는 오래된 임시 파일 삭제 실패를 추적한다. |
| `relay_temp_cleanup_lock_release_failed` | 임시 파일 cleanup lock 해제에 실패한 때 | `room_id` | 있음 | cleanup lock 해제 실패로 후속 정리가 지연될 수 있는 상황이다. |

## 현재 코드에 없는 로그

아래 항목은 이전 검토 목록에는 있었지만, 현재 코드에는 아직 구조화 로그로 들어가 있지 않다.

| 이벤트 또는 항목 | 현재 상태 | 이유 |
| --- | --- | --- |
| `audit_export` | 미구현 | 감사 로그 CSV 다운로드 API가 아직 없다. export API가 생기면 `actor_id`, `filter`, `result`를 `logs.audit`로 남기면 된다. |
| WebSocket close 실패 구조화 로그 | 미구현 | 현재 `WebSocketSessionRegistry`는 close 실패 시 일반 `log.warn`만 남긴다. `room_id`, `uuid`, `session_id`, `close_reason`을 가진 `RelayRoomEventLogger.websocketWarn` 형태로는 아직 남기지 않는다. |

## 운영 시 읽는 법

정상 흐름은 대체로 `relay_room_created` -> `relay_participant_joined` -> `relay_ws_connected` -> `relay_game_started` -> `relay_part_started` -> `relay_drawing_submitted` 또는 `relay_part_auto_submitted` -> `relay_all_parts_completed` -> `relay_result_created` -> `relay_room_closed` -> `relay_temp_cleanup_completed` 순서로 이어진다.

사용자 연결 문제를 볼 때는 `relay_ws_connected`, `relay_ws_disconnected`, `relay_ws_reconnected`, `relay_participant_dropped`를 같은 `room_id`와 `uuid`로 묶어서 보면 된다. `relay_ws_disconnected`만 있고 `relay_ws_reconnected`가 없으며 이후 `relay_participant_dropped`가 찍히면, 사용자가 재연결 유예 시간 안에 돌아오지 못해 이탈 확정된 것이다.

파트 마감 문제를 볼 때는 `relay_part_started.part_deadline_at`, `relay_part_time_up.submit_grace_deadline_at`, `relay_part_auto_submitted.reason`을 함께 보면 된다. `relay_part_time_up`은 프론트에게 deadline 종료를 알려주는 이벤트이고, 실제 서버 fallback 확정은 이후 `relay_part_auto_submitted`로 남는다.

결과물 생성 장애는 `relay_finalization_failed.stage`가 가장 중요하다. `compose`는 이미지 합성 또는 원본 다운로드 문제, `minio_upload`는 결과물 업로드 문제, `artifact_meta`는 메타데이터 JSON 생성 문제, `db_save`는 artifact/gallery 저장 문제, `redis_update`는 최종 Redis 상태 전환 문제로 보면 된다.
