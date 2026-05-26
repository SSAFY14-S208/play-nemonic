"use client";

import { useEffect } from "react";

import { usePhoneLauncherStore } from "@/shared/stores";

import { RelayFloatingControls } from './components';
import RelayBoothView from './views/RelayBoothView';
import "./relay-drawing.css";

// 라우트: /relay-drawing
// 부스(랜딩) 화면만 담당. 방 생성/입장 액션은 RelayBoothView 내부에서 처리하고,
// 성공 시 router.push(`/relay-drawing/${roomCode}`)로 RelayRoomPage로 넘어간다.
//
// `font-paperlogy`는 feature 도메인 폰트 마커. relay-drawing.css가 이 마커
// 안의 typography utility들을 Paperlogy로 override한다.
export default function RelayDrawingPage() {
  // 부스 페이지에서 floating PhoneLauncher를 숨기고 인라인 버튼으로 대체.
  const setLauncherHidden = usePhoneLauncherStore(
    (state) => state.setLauncherHidden,
  );
  useEffect(() => {
    setLauncherHidden(true);
    return () => setLauncherHidden(false);
  }, [setLauncherHidden]);

  return (
    <div className="font-paperlogy min-h-screen bg-relay-background text-relay-ink">
      <RelayBoothView />
      <RelayFloatingControls buttonClassName="size-14" />
    </div>
  );
}
