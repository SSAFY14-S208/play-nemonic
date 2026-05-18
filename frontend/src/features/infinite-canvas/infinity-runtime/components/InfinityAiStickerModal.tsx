"use client";

import { KeyboardEvent, useCallback, useState } from "react";
import Image from "next/image";
import { ImagePlus, Loader2, RefreshCcw, Sparkles, X } from "lucide-react";

import { cn } from "@/shared/libs";
import type { InfiniteCanvasAiStickerCreateResponse } from "@/shared/types";

interface InfinityAiStickerModalProps {
  open: boolean;
  loading: boolean;
  previewSticker: InfiniteCanvasAiStickerCreateResponse | null;
  onClose: () => void;
  onSubmit: (prompt: string) => Promise<boolean>;
  onAttach: () => boolean;
  onClearPreview: () => void;
}

const EXAMPLE_PROMPTS = [
  "바이올린 켜는 토끼",
  "우주복 입은 고양이",
  "별사탕 들고 있는 강아지",
] as const;

export function InfinityAiStickerModal({
  open,
  loading,
  previewSticker,
  onClose,
  onSubmit,
  onAttach,
  onClearPreview,
}: InfinityAiStickerModalProps) {
  const [prompt, setPrompt] = useState("");

  const submitPrompt = useCallback(async () => {
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt || loading) return;

    await onSubmit(trimmedPrompt);
  }, [loading, onSubmit, prompt]);

  const submitPromptWithKeyboard = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
      event.preventDefault();
      void submitPrompt();
    }
  };

  if (!open) return null;

  return (
    <div className="fixed bottom-8 left-[244px] z-30 grid w-[760px] grid-cols-[360px_1fr] gap-3 rounded-[28px] border border-[#bfe7ff] bg-white/96 p-4 shadow-[0_18px_45px_rgba(45,103,184,0.24)] backdrop-blur">
      <div>
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-[15px] font-bold text-[#203761]">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#eaf6ff] text-[#2f80ed]">
              <Sparkles size={17} />
            </span>
            AI 스티커 만들기
          </div>
          <button
            type="button"
            title="닫기"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-[#7a8aa8] transition hover:bg-[#eef6ff] hover:text-[#203761]"
          >
            <X size={17} />
          </button>
        </div>

        <div className="space-y-3">
          <div className="rounded-[20px] bg-[#f2f8ff] p-3">
            <label className="mb-2 block text-[12px] font-semibold text-[#577092]">
              만들고 싶은 스티커를 적어주세요
            </label>
            <textarea
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              onKeyDown={submitPromptWithKeyboard}
              placeholder="예: 바이올린 켜는 토끼"
              maxLength={300}
              className="min-h-[92px] w-full resize-none rounded-[16px] border border-[#d7e9ff] bg-white px-3 py-2 text-[14px] font-medium text-[#203761] outline-none transition placeholder:text-[#9aabc2] focus:border-[#70c8ff] focus:ring-2 focus:ring-[#cdeeff] disabled:opacity-60"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {EXAMPLE_PROMPTS.map((examplePrompt) => (
              <button
                key={examplePrompt}
                type="button"
                disabled={loading}
                onClick={() => setPrompt(examplePrompt)}
                className="rounded-full bg-[#edf7ff] px-3 py-1.5 text-[12px] font-semibold text-[#35659d] transition hover:bg-[#dff1ff] disabled:opacity-60"
              >
                {examplePrompt}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => void submitPrompt()}
            disabled={!prompt.trim() || loading}
            className={cn(
              "flex h-11 w-full items-center justify-center gap-2 rounded-[18px] text-[14px] font-extrabold text-white shadow-[0_10px_22px_rgba(47,128,237,0.24)] transition",
              loading
                ? "bg-[#94bff1]"
                : "bg-[#2f80ed] hover:bg-[#246fd4] disabled:bg-[#b8cce4]",
            )}
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
            {loading ? "스티커 생성 중" : previewSticker ? "다시 만들기" : "스티커 생성하기"}
          </button>
        </div>
      </div>

      <section className="flex min-h-[286px] flex-col rounded-[24px] bg-[#f6fbff] p-3">
        <div className="mb-2 flex items-center justify-between">
          <div className="text-[13px] font-extrabold text-[#203761]">생성 결과</div>
          {previewSticker ? (
            <button
              type="button"
              onClick={onClearPreview}
              className="flex h-8 items-center gap-1 rounded-full bg-white px-3 text-[12px] font-bold text-[#6780a1] shadow-sm transition hover:text-[#203761]"
            >
              <RefreshCcw size={13} />
              비우기
            </button>
          ) : null}
        </div>

        <div className="flex flex-1 items-center justify-center rounded-[22px] border border-[#d9ecff] bg-white">
          {loading ? (
            <div className="flex flex-col items-center gap-3 text-center">
              <span className="flex h-14 w-14 items-center justify-center rounded-full bg-[#e7f5ff] text-[#2f80ed]">
                <Loader2 size={24} className="animate-spin" />
              </span>
              <div>
                <div className="text-[14px] font-extrabold text-[#203761]">
                  AI로 스티커를 생성하고 있어요
                </div>
                <div className="mt-1 text-[12px] font-semibold text-[#8aa0ba]">
                  완성되면 여기에서 먼저 확인할 수 있어요
                </div>
              </div>
            </div>
          ) : previewSticker ? (
            <div className="flex w-full flex-col items-center gap-3 p-3">
              <div className="flex h-[178px] w-full items-center justify-center overflow-hidden rounded-[20px] bg-[linear-gradient(45deg,#f4f8ff_25%,transparent_25%),linear-gradient(-45deg,#f4f8ff_25%,transparent_25%),linear-gradient(45deg,transparent_75%,#f4f8ff_75%),linear-gradient(-45deg,transparent_75%,#f4f8ff_75%)] bg-[length:18px_18px] bg-[position:0_0,0_9px,9px_-9px,-9px_0]">
                <Image
                  src={previewSticker.imageUrl}
                  alt="생성된 AI 스티커"
                  width={240}
                  height={240}
                  unoptimized
                  className="max-h-[158px] max-w-[230px] object-contain drop-shadow-[0_12px_22px_rgba(52,87,135,0.18)]"
                />
              </div>
              <button
                type="button"
                onClick={onAttach}
                className="flex h-11 w-full items-center justify-center gap-2 rounded-[18px] bg-[#2f80ed] text-[14px] font-extrabold text-white shadow-[0_10px_22px_rgba(47,128,237,0.24)] transition hover:bg-[#246fd4]"
              >
                <ImagePlus size={16} />
                캔버스에 붙이기
              </button>
            </div>
          ) : (
            <div className="px-6 text-center">
              <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-[#eaf6ff] text-[#2f80ed]">
                <Sparkles size={22} />
              </div>
              <div className="text-[14px] font-extrabold text-[#203761]">
                프롬프트를 입력해 스티커를 만들어보세요
              </div>
              <div className="mt-1 text-[12px] font-semibold text-[#8aa0ba]">
                결과를 확인한 뒤 마음에 들 때만 캔버스에 붙일 수 있어요
              </div>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
