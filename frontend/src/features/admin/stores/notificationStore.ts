// admin 페이지는 루트와 별개로 운영되는 페이지이기 때문에 전역변수를 features에 따로 관리합니다.
import { create } from "zustand";

export type AdminNotification = {
  id: string;
  title: string;
  body: string;
  receivedAt: string;
};

// 현재는 API 연결이 없어 목데이터로 알림 데이터 관리
const SAMPLE_NOTIFICATIONS: AdminNotification[] = [
  {
    id: "n-1",
    title: "신규 신고 접수",
    body: "커뮤니티 캔버스에서 1건의 신고가 접수되었습니다.",
    receivedAt: "방금 전",
  },
  {
    id: "n-2",
    title: "CS 미처리 알림",
    body: "24시간 이상 미처리된 CS 문의가 3건 있습니다.",
    receivedAt: "10분 전",
  },
  {
    id: "n-3",
    title: "감사 로그 백업 완료",
    body: "오늘 자 감사 로그가 정상적으로 보관되었습니다.",
    receivedAt: "1시간 전",
  },
];

interface NotificationStore {
  notifications: AdminNotification[];
  hasUnread: boolean;
  isModalOpen: boolean;
  openModal: () => void;
  closeModal: () => void;
  markAllRead: () => void;
}

export const useNotificationStore = create<NotificationStore>((set) => ({
  notifications: SAMPLE_NOTIFICATIONS,
  hasUnread: true,
  isModalOpen: false,
  openModal: () => set({ isModalOpen: true, hasUnread: false }),
  closeModal: () => set({ isModalOpen: false }),
  markAllRead: () => set({ hasUnread: false }),
}));
