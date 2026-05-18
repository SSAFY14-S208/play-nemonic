// 감사 로그 이벤트 화이트리스트. observability 명세(backend/docs/product-spec/08-observability.md)의
// "기록 대상 이벤트" 목록을 그대로 옮긴 것이며, 필터 드롭다운 옵션과 한국어 라벨 매핑에 사용한다.

export interface AuditEventDescriptor {
  /** OpenSearch `event_name` 값 — 백엔드 emit 키와 일치해야 한다. */
  value: string
  /** UI 라벨. */
  label: string
  /** UI 그룹 헤더용 카테고리 라벨. */
  category: string
}

// 카테고리는 사이드바 메뉴 구조와 일치시켜 운영자가 동일한 멘탈 모델로 탐색하도록 한다.
// 라벨은 한국어로 통일하고, 기술 약어(GMS·Electron·CS)는 사용자 가시 영역에서 풀어 쓴다.
export const AUDIT_EVENT_DESCRIPTORS: readonly AuditEventDescriptor[] = [
  // 인증
  { value: 'admin_login', label: '관리자 로그인', category: '인증' },
  { value: 'admin_logout', label: '관리자 로그아웃', category: '인증' },
  { value: 'admin_login_failed', label: '로그인 실패', category: '인증' },

  // 관리자 계정
  { value: 'admin_account_create', label: '관리자 계정 생성', category: '관리자 계정' },
  { value: 'admin_account_delete', label: '관리자 계정 삭제', category: '관리자 계정' },

  // 커뮤니티 캔버스
  { value: 'memo_soft_delete', label: '메모 숨김', category: '커뮤니티 캔버스' },
  { value: 'memo_restore', label: '메모 복원', category: '커뮤니티 캔버스' },
  { value: 'memo_bulk_soft_delete', label: '메모 일괄 숨김', category: '커뮤니티 캔버스' },
  { value: 'memo_bulk_restore', label: '메모 일괄 복원', category: '커뮤니티 캔버스' },
  { value: 'report_review_decided', label: '신고 검토 결정', category: '커뮤니티 캔버스' },
  { value: 'ai_moderation_override', label: 'AI 자동 처리 수동 변경', category: '커뮤니티 캔버스' },

  // 활성 컨텐츠
  { value: 'relay_room_force_close', label: '릴레이 방 강제 종료', category: '활성 컨텐츠' },
  { value: 'flipbook_room_force_close', label: '플립북 방 강제 종료', category: '활성 컨텐츠' },
  { value: 'infinite_canvas_force_close', label: '무한 캔버스 강제 종료', category: '활성 컨텐츠' },

  // 운세 프롬프트
  { value: 'prompt_update', label: '운세 프롬프트 수정', category: '운세 프롬프트' },
  { value: 'prompt_rollback', label: '운세 프롬프트 되돌리기', category: '운세 프롬프트' },

  // 컨텐츠 파라미터
  { value: 'param_change', label: '컨텐츠 파라미터 변경', category: '컨텐츠 파라미터' },

  // 고객 문의
  { value: 'inquiry_status_change', label: '문의 상태 변경', category: '고객 문의' },
  { value: 'inquiry_reply_send', label: '문의 답변 발송', category: '고객 문의' },
  { value: 'inquiry_internal_memo', label: '문의 내부 메모', category: '고객 문의' },

  // 알림 및 배포
  { value: 'notification_send', label: '알림 발송', category: '알림 및 배포' },
  { value: 'electron_channel_change', label: '데스크톱 앱 채널 변경', category: '알림 및 배포' },
  { value: 'electron_release_publish', label: '데스크톱 앱 배포 발행', category: '알림 및 배포' },

  // 시스템
  { value: 'audit_export', label: '감사 로그 내보내기', category: '시스템' },
]

export const AUDIT_EVENT_LABEL_MAP: Readonly<Record<string, string>> = Object.fromEntries(
  AUDIT_EVENT_DESCRIPTORS.map((descriptor) => [descriptor.value, descriptor.label]),
)

// 대분류 카테고리 목록 — descriptors의 등장 순서를 보존해 중복 제거.
export const AUDIT_CATEGORIES: readonly string[] = (() => {
  const seen = new Set<string>()
  const ordered: string[] = []
  for (const descriptor of AUDIT_EVENT_DESCRIPTORS) {
    if (seen.has(descriptor.category)) continue
    seen.add(descriptor.category)
    ordered.push(descriptor.category)
  }
  return ordered
})()

export type AuditLogRange = '7d' | '30d' | '90d'

export const AUDIT_RANGE_OPTIONS: readonly { value: AuditLogRange; label: string }[] = [
  { value: '7d', label: '최근 7일' },
  { value: '30d', label: '최근 30일' },
  { value: '90d', label: '최근 90일' },
]

export const AUDIT_RANGE_DAYS: Readonly<Record<AuditLogRange, number>> = {
  '7d': 7,
  '30d': 30,
  '90d': 90,
}
