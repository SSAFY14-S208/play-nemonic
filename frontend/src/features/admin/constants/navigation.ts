import {
  BarChart3,
  BookOpen,
  FileText,
  Flag,
  Image as ImageIcon,
  Infinity as InfinityIcon,
  Layers,
  LayoutDashboard,
  MessageSquare,
  Pencil,
  Shield,
  Sliders,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type AdminNavItem = {
  key: string;
  label: string;
  href: string;
  icon: LucideIcon;
  pageTitle: string;
  pageDescription: string;
  children?: AdminNavItem[];
  requiredRole?: string;
};

export type AdminNavGroup = {
  key: string;
  label: string;
  items: AdminNavItem[];
};

const ACTIVE_CONTENT_DESCRIPTION =
  "실시간 진행중 방 조회. 라운드별 드로잉/완성 결과물(GIF)은 열람하지 않으며 메타데이터로만 관리합니다.";

export const ADMIN_NAVIGATION: AdminNavGroup[] = [
  {
    key: "overview",
    label: "OVERVIEW",
    items: [
      {
        key: "dashboard",
        label: "대시보드",
        href: "/admin/dashboard",
        icon: LayoutDashboard,
        pageTitle: "대시보드",
        pageDescription: "네모닉 월드의 다양한 데이터를 한눈에 조회합니다.",
      },
    ],
  },
  {
    key: "content",
    label: "CONTENT",
    items: [
      {
        key: "community-canvas",
        label: "커뮤니티 캔버스",
        href: "/admin/community-canvas",
        icon: ImageIcon,
        pageTitle: "커뮤니티 캔버스 관리",
        pageDescription:
          "실시간으로 활성화 되어있는 50개의 메모를 조회 및 관리합니다.",
      },
      {
        key: "active-content",
        label: "활성 컨텐츠",
        href: "/admin/active-content",
        icon: Layers,
        pageTitle: "활성 컨텐츠 관리",
        pageDescription: "",
        children: [
          {
            key: "infinite-canvas",
            label: "무한 캔버스",
            href: "/admin/active-content/infinite-canvas",
            icon: InfinityIcon,
            pageTitle: "무한 캔버스 관리",
            pageDescription: ACTIVE_CONTENT_DESCRIPTION,
          },
          {
            key: "relay-drawing",
            label: "릴레이 드로잉",
            href: "/admin/active-content/relay-drawing",
            icon: Pencil,
            pageTitle: "릴레이 드로잉 관리",
            pageDescription: ACTIVE_CONTENT_DESCRIPTION,
          },
          {
            key: "flipbook",
            label: "플립북",
            href: "/admin/active-content/flipbook",
            icon: BookOpen,
            pageTitle: "플립북 관리",
            pageDescription: ACTIVE_CONTENT_DESCRIPTION,
          },
        ],
      },
      {
        key: "gms-prompts",
        label: "GMS 프롬프트 관리",
        href: "/admin/gms-prompts",
        icon: FileText,
        pageTitle: "GMS 프롬프트 관리",
        pageDescription: "운세 생성에 사용하는 프롬프트를 수정·관리합니다.",
      },
      {
        key: "content-parameters",
        label: "컨텐츠 파라미터",
        href: "/admin/content-parameters",
        icon: Sliders,
        pageTitle: "컨텐츠 파라미터 관리",
        pageDescription:
          "서비스 운영에 필요한 주요 파라미터를 동적으로 관리합니다.",
      },
    ],
  },
  {
    key: "moderation",
    label: "MODERATION",
    items: [
      {
        key: "cs-inquiries",
        label: "CS 문의",
        href: "/admin/cs-inquiries",
        icon: MessageSquare,
        pageTitle: "CS 문의",
        pageDescription:
          "사용자 문의를 접수·처리하고, 24시간 이상 미처리 건은 우선순위로 표시합니다.",
      },
    ],
  },
  {
    key: "analytics",
    label: "ANALYTICS",
    items: [
      {
        key: "analytics",
        label: "통계 및 분석",
        href: "/admin/analytics",
        icon: BarChart3,
        pageTitle: "통계 및 분석",
        pageDescription: "서비스 운영 현황과 사용자 행동을 분석합니다.",
      },
    ],
  },
  {
    key: "system",
    label: "SYSTEM",
    items: [
      {
        key: "backoffice-management",
        label: "백오피스 관리",
        href: "/admin/backoffice-management",
        icon: Shield,
        pageTitle: "백오피스 관리",
        pageDescription:
          "관리자 계정을 생성·조회·삭제합니다. 슈퍼 관리자만 접근할 수 있습니다.",
        requiredRole: "super_admin",
      },
    ],
  },
];

const flattenLeafItems = (items: AdminNavItem[]): AdminNavItem[] =>
  items.flatMap((item) =>
    item.children ? flattenLeafItems(item.children) : [item],
  );

const ADMIN_LEAF_ITEMS: AdminNavItem[] = ADMIN_NAVIGATION.flatMap((group) =>
  flattenLeafItems(group.items),
);

export function findActiveAdminNavItem(pathname: string): AdminNavItem | null {
  const matches = ADMIN_LEAF_ITEMS.filter(
    (item) => pathname === item.href || pathname.startsWith(`${item.href}/`),
  );
  if (matches.length === 0) return null;
  return matches.reduce((longest, current) =>
    current.href.length > longest.href.length ? current : longest,
  );
}
