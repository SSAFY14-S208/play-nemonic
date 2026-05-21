"use client";

import Link from "next/link";
import Image from "next/image";
import { Zap } from "lucide-react";
import {
  type FormEvent,
  type KeyboardEvent,
  useEffect,
  useRef,
  useState,
} from "react";
import { phoneIconEdit, phoneProfileAvatar } from "@/shared/assets";
import { cn, logEvent } from "@/shared/libs";
import { PHONE_APP_SHORTCUTS, PHONE_COLORS } from "../constants";
import { usePhoneStore } from "../phoneStore";

const FALLBACK_NICKNAME = "게스트";
const NICKNAME_MAX_LENGTH = 10;

export function PhoneHomeScreen() {
  const showDrawing = usePhoneStore((state) => state.showDrawing);
  const showGallery = usePhoneStore((state) => state.showGallery);
  const showInquiry = usePhoneStore((state) => state.showInquiry);
  const showTeleport = usePhoneStore((state) => state.showTeleport);
  const profile = usePhoneStore((state) => state.profile);
  const profileStatus = usePhoneStore((state) => state.profileStatus);
  const nicknameUpdateStatus = usePhoneStore(
    (state) => state.nicknameUpdateStatus,
  );
  const nicknameFieldError = usePhoneStore((state) => state.nicknameFieldError);
  const loadProfile = usePhoneStore((state) => state.loadProfile);
  const updateNickname = usePhoneStore((state) => state.updateNickname);
  const clearNicknameFieldError = usePhoneStore(
    (state) => state.clearNicknameFieldError,
  );

  useEffect(() => {
    if (profileStatus === "idle" || profileStatus === "error") {
      void loadProfile();
    }
  }, [loadProfile, profileStatus]);

  const [isEditing, setIsEditing] = useState(false);
  const [draftNickname, setDraftNickname] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isEditing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [isEditing]);

  const displayNickname = profile?.nickname?.trim() || FALLBACK_NICKNAME;
  const isProfileLoading = profileStatus === "loading" && !profile;
  const isSavingNickname = nicknameUpdateStatus === "loading";
  const featuredShortcut = PHONE_APP_SHORTCUTS.find(
    (shortcut) => shortcut.key === "teleport",
  );
  const regularShortcuts = PHONE_APP_SHORTCUTS.filter(
    (shortcut) => shortcut.key !== "teleport",
  );

  const startEditing = () => {
    setDraftNickname(profile?.nickname ?? "");
    clearNicknameFieldError();
    setIsEditing(true);
  };

  const cancelEditing = () => {
    setIsEditing(false);
    setDraftNickname("");
    clearNicknameFieldError();
  };

  const submitNickname = async () => {
    const trimmed = draftNickname.trim();
    if (!trimmed || trimmed === profile?.nickname) {
      cancelEditing();
      return;
    }
    const success = await updateNickname(trimmed);
    if (success) {
      setIsEditing(false);
      setDraftNickname("");
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void submitNickname();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      cancelEditing();
    }
  };

  return (
    <div className="phone-home-body-m flex h-full flex-col bg-surface-default">
      <section
        className="h-[9rem] px-[1.375rem] pt-[4.15rem] text-white"
        style={{ background: PHONE_COLORS.homeHeader }}
      >
        <div className="flex items-center gap-[0.85rem]">
          <Image
            src={phoneProfileAvatar}
            alt={`${displayNickname} 프로필 이미지`}
            className="size-[4.3rem] shrink-0 rounded-full"
            priority
          />
          <div className="min-w-0 flex-1">
            {isEditing ? (
              <form onSubmit={handleSubmit} className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <input
                    ref={inputRef}
                    type="text"
                    value={draftNickname}
                    onChange={(event) => setDraftNickname(event.target.value)}
                    onKeyDown={handleKeyDown}
                    onBlur={() => void submitNickname()}
                    maxLength={NICKNAME_MAX_LENGTH}
                    disabled={isSavingNickname}
                    aria-label="닉네임 입력"
                    className="phone-home-nickname-sb min-w-0 flex-1 rounded-[0.35rem] border border-white/40 bg-white/15 px-2 py-0.5 text-white outline-none placeholder:text-white/60 focus:border-white"
                  />
                  {isSavingNickname && (
                    <span
                      aria-hidden
                      className="size-3 shrink-0 animate-spin rounded-full border-2 border-white/40 border-t-white"
                    />
                  )}
                </div>
                {nicknameFieldError && (
                  <span className="caption-r text-red-200">
                    {nicknameFieldError}
                  </span>
                )}
              </form>
            ) : (
              <div className="flex items-center gap-2">
                {isProfileLoading ? (
                  <span
                    aria-hidden
                    className="block h-5 w-32 animate-pulse rounded-md bg-white/30"
                  />
                ) : (
                  <h2 className="phone-home-nickname-sb truncate text-white">
                    {displayNickname}
                  </h2>
                )}
                <button
                  type="button"
                  aria-label="프로필 수정"
                  onClick={startEditing}
                  disabled={isProfileLoading}
                  className="flex size-8 shrink-0 items-center justify-center text-white transition-transform hover:scale-105 focus-visible:outline-none disabled:opacity-50"
                >
                  <Image
                    src={phoneIconEdit}
                    alt=""
                    aria-hidden
                    className="size-6 object-contain"
                  />
                </button>
              </div>
            )}
          </div>
        </div>
      </section>

      <div className="flex-1 overflow-y-auto bg-white px-0 pt-7 pb-8">
        {featuredShortcut && (
          <button
            type="button"
            disabled={!featuredShortcut.isEnabled}
            onClick={showTeleport}
            className={cn(
              "group mx-auto mb-7 flex min-h-[4.6rem] w-[16.25rem] items-center gap-3 rounded-[0.65rem] border border-[#c9e9ff] bg-[#eff9ff] px-3 text-left shadow-[0_0.35rem_1rem_rgba(85,173,240,0.16)] transition duration-200",
              featuredShortcut.isEnabled
                ? "hover:-translate-y-0.5 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2"
                : "cursor-default",
            )}
          >
            <span
              aria-hidden
              className="grid size-12 shrink-0 place-items-center rounded-[0.9rem] bg-[#55adf0] text-white shadow-[inset_0_0.08rem_0_rgba(255,255,255,0.35),0_0.35rem_0.7rem_rgba(85,173,240,0.25)] transition duration-200 group-hover:scale-105"
            >
              <Zap className="size-6" strokeWidth={2.5} />
            </span>
            <span className="min-w-0">
              <span className="phone-home-app-label-m block text-fg-primary">
                {featuredShortcut.label}
              </span>
              <span className="phone-caption-r mt-1 block text-fg-secondary">
                허브와 미니게임으로 바로 이동
              </span>
            </span>
          </button>
        )}
        <div className="mx-auto grid w-[16.25rem] grid-cols-2 gap-x-8 gap-y-[2.35rem]">
          {regularShortcuts.map(
            (shortcut) => {
              if (!("asset" in shortcut)) return null;

              const { action, key, label, asset, externalUrl, isEnabled } =
                shortcut;

              const handleClick = () => {
                switch (action) {
                  case "open-drawing":
                    showDrawing();
                    return;
                  case "open-gallery":
                    showGallery();
                    return;
                  case "open-inquiry":
                    showInquiry();
                    return;
                  default:
                    return;
                }
              };

              const shortcutContent = (
                <>
                  <Image
                    src={asset}
                    alt=""
                    aria-hidden
                    className="size-20 object-contain transition duration-200 group-hover:scale-105"
                  />
                  <span className="phone-home-app-label-m text-center text-fg-primary">
                    {label}
                  </span>
                </>
              );

              if (action === "open-external" && externalUrl) {
                return (
                  <Link
                    key={key}
                    href={externalUrl}
                    target="_blank"
                    rel="noreferrer"
                    // 핸드폰 모달 → 외부 공식몰 이동 분석용 emit. shortcut key를 metadata에 두어
                    // 향후 다른 외부 링크가 추가되어도 동일 이벤트에서 분리 집계 가능.
                    onClick={() => {
                      logEvent("phone_official_store_clicked", {
                        metadata: {
                          shortcut_key: key,
                          destination: externalUrl,
                        },
                      });
                    }}
                    className="group flex min-h-[7.45rem] w-[7.15rem] flex-col items-center justify-start gap-2 transition duration-200 hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2"
                  >
                    {shortcutContent}
                  </Link>
                );
              }

              return (
                <button
                  key={key}
                  type="button"
                  disabled={!isEnabled}
                  onClick={handleClick}
                  className={cn(
                    "group flex min-h-[7.45rem] w-[7.15rem] flex-col items-center justify-start gap-2 transition duration-200",
                    isEnabled
                      ? "hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2"
                      : "cursor-default",
                  )}
                >
                  {shortcutContent}
                </button>
              );
            },
          )}
        </div>
      </div>
    </div>
  );
}
