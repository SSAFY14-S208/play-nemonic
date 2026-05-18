"use client";

import { FormEvent, useState } from "react";
import { Sparkles, X } from "lucide-react";

import { cn } from "@/shared/libs";

interface InfinityAiStickerModalProps {
  open: boolean;
  loading: boolean;
  onClose: () => void;
  onSubmit: (prompt: string) => Promise<boolean>;
}

const EXAMPLE_PROMPTS = [
  "바이올린 켜는 토끼",
  "우주복 입은 고양이",
  "별사탕 들고 있는 강아지",
] as const;

export function InfinityAiStickerModal({
  open,
  loading,
  onClose,
  onSubmit,
}: InfinityAiStickerModalProps) {
  const [prompt, setPrompt] = useState("");

  if (!open) return null;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt || loading) return;

    const created = await onSubmit(trimmedPrompt);
    if (created) {
      setPrompt("");
    }
  };

  return (
    <div className="fixed bottom-8 left-[244px] z-30 w-[360px] rounded-[26px] border border-[#bfe7ff] bg-white/96 p-4 shadow-[0_18px_45px_rgba(45,103,184,0.24)] backdrop-blur">
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

      <form onSubmit={submit} className="space-y-3">
        <div className="rounded-[20px] bg-[#f2f8ff] p-3">
          <label className="mb-2 block text-[12px] font-semibold text-[#577092]">
            만들고 싶은 스티커를 적어주세요
          </label>
          <textarea
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            placeholder="예: 바이올린 켜는 토끼"
            maxLength={300}
            disabled={loading}
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
          type="submit"
          disabled={!prompt.trim() || loading}
          className={cn(
            "flex h-11 w-full items-center justify-center gap-2 rounded-[18px] text-[14px] font-extrabold text-white shadow-[0_10px_22px_rgba(47,128,237,0.24)] transition",
            loading
              ? "bg-[#94bff1]"
              : "bg-[#2f80ed] hover:bg-[#246fd4] disabled:bg-[#b8cce4]",
          )}
        >
          <Sparkles size={16} />
          {loading ? "스티커를 만들고 있어요" : "캔버스에 추가하기"}
        </button>
      </form>
    </div>
  );
}
