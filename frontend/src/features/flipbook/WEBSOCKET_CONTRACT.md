# Flipbook WebSocket Contract

프론트는 현재 데모 상태를 유지하면서도, 아래 메시지 계약대로 서버 연동을 붙일 수 있게 정리되어 있다.

## 연결

- URL: `runtime.websocketUrl`
- 환경 변수: `NEXT_PUBLIC_WEBSOCKET_URL`
- 식별: `roomId + userUuid`
- `userUuid` 출처: `shared/stores/userStore`

## 클라이언트 → 서버

타입 정의는 `src/shared/types/flipbook.ts`의 `FlipbookClientMessage`를 기준으로 한다.

- `flipbook.room.create`: 방 생성 요청
- `flipbook.room.join`: roomId 또는 roomCode로 방 입장/재입장 요청
- `flipbook.settings.update`: 제한 시간/라운드 수 변경
- `flipbook.game.start`: 방장이 게임 시작
- `flipbook.frame.draft`: 현재 프레임 임시 저장 동기화
- `flipbook.frame.submit`: 현재 프레임 제출 또는 자동 제출
- `flipbook.room.leave`: 중도 이탈

모든 클라이언트 메시지는 `requestId`를 포함한다.

## 서버 → 클라이언트

타입 정의는 `src/shared/types/flipbook.ts`의 `FlipbookServerMessage`를 기준으로 한다.

- `flipbook.snapshot`: 방 전체 상태 스냅샷
- `flipbook.assignment.changed`: 현재 사용자가 그릴 프레임 배정
- `flipbook.participants.changed`: 참여자/방장/접속 상태 변경
- `flipbook.result.completed`: 결과 프레임 배열 + GIF URL
- `flipbook.error`: 입장 거부, 재접속 실패, 권한 없음 등

## 프론트 적용 지점

- `useFlipbookRealtimeStore`: 서버 스냅샷, 연결 상태, outbound queue 보관
- `useFlipbook`: 현재 데모 UI 상태를 `FlipbookSessionSnapshot` 형태로 투영하고, 버튼/제출 액션을 `FlipbookClientMessage`로 큐에 쌓음
- 추후 실제 transport는 `shared/libs/webSocketClient.ts` 같은 순수 WebSocket 래퍼를 만든 뒤, feature hook에서 큐를 전송하고 서버 메시지를 `applyServerMessage`로 넣으면 된다.

## 이탈/재접속 기준

- 신규 UUID + 진행 중: 서버가 `flipbook.error`로 거부
- 기존 UUID + 10초 이내: `flipbook.snapshot`으로 복귀
- 기존 UUID + 10초 초과: 자동 제출 처리 후 `flipbook.error`
- 중도 이탈 프레임은 서버가 empty 여부를 판단하고 결과에서 compact한다.
