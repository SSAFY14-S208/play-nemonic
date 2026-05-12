"use client";

import { useEffect, useMemo, useState, useTransition } from "react";
import { toast } from "sonner";

import {
  ApiError,
  deleteGmsPrompt,
  getGmsPrompt,
  getGmsPromptList,
  patchGmsPrompt,
  postGmsPrompt,
} from "@/shared/apis";
import type { GmsFeatureType, GmsPromptResponse } from "@/shared/types";

export interface GmsPromptEditorState {
  name: string;
  content: string;
  featureType: GmsFeatureType;
}

export type FeatureFilter = GmsFeatureType | "ALL";

const EMPTY_EDITOR: GmsPromptEditorState = {
  name: "",
  content: "",
  featureType: "fortune",
};

const toEditor = (prompt: GmsPromptResponse): GmsPromptEditorState => ({
  name: prompt.name,
  content: prompt.content,
  featureType: prompt.featureType,
});

export function useGmsPrompts() {
  const [prompts, setPrompts] = useState<GmsPromptResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [featureFilter, setFeatureFilter] = useState<FeatureFilter>("ALL");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [editor, setEditor] = useState<GmsPromptEditorState>(EMPTY_EDITOR);
  const [isMutating, startMutationTransition] = useTransition();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await getGmsPromptList({ page: 0, size: 20 });
        if (cancelled) return;
        setPrompts(response.items);
        setLoadError(null);
        if (response.items.length > 0) {
          setSelectedId(response.items[0].id);
          setEditor(toEditor(response.items[0]));
        }
      } catch (caughtError) {
        if (cancelled) return;
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : "프롬프트 목록을 불러오지 못했어요";
        setLoadError(message);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const filteredPrompts = useMemo(() => {
    if (featureFilter === "ALL") return prompts;
    return prompts.filter((prompt) => prompt.featureType === featureFilter);
  }, [prompts, featureFilter]);

  const selected = useMemo(
    () => prompts.find((prompt) => prompt.id === selectedId) ?? null,
    [prompts, selectedId],
  );

  const isDirty = useMemo(() => {
    if (isCreating) {
      return Boolean(editor.name || editor.content);
    }
    if (!selected) return false;
    return (
      editor.name !== selected.name ||
      editor.content !== selected.content ||
      editor.featureType !== selected.featureType
    );
  }, [editor, selected, isCreating]);

  const select = (id: number) => {
    const target = prompts.find((prompt) => prompt.id === id);
    if (!target) return;
    setSelectedId(id);
    setIsCreating(false);
    setEditor(toEditor(target));

    // 백엔드가 별도로 제공하는 상세 조회로 최신값 동기화 — 다른 운영자가 동시에
    // 수정했을 가능성. 사용자가 아직 손대지 않은 editor만 안전하게 갱신한다.
    void (async () => {
      try {
        const detail = await getGmsPrompt(id);
        setPrompts((prev) =>
          prev.map((prompt) => (prompt.id === id ? detail : prompt)),
        );
        setEditor((prev) => {
          const editorMatchesCached =
            prev.name === target.name &&
            prev.content === target.content &&
            prev.featureType === target.featureType;
          return editorMatchesCached ? toEditor(detail) : prev;
        });
      } catch {
        // 상세 조회 실패는 toast 없이 무시 — 목록 캐시가 fallback 역할.
      }
    })();
  };

  const startCreate = () => {
    setIsCreating(true);
    setSelectedId(null);
    setEditor(EMPTY_EDITOR);
  };

  const cancelCreate = () => {
    setIsCreating(false);
    if (prompts.length > 0) {
      const first = prompts[0];
      setSelectedId(first.id);
      setEditor(toEditor(first));
    } else {
      setEditor(EMPTY_EDITOR);
    }
  };

  const setEditorField = <K extends keyof GmsPromptEditorState>(
    field: K,
    value: GmsPromptEditorState[K],
  ) => {
    setEditor((prev) => ({ ...prev, [field]: value }));
  };

  const save = () => {
    if (isMutating || !isDirty) return;

    if (isCreating) {
      if (!editor.name.trim() || !editor.content.trim()) {
        toast.error("이름과 본문을 입력하세요");
        return;
      }
      startMutationTransition(async () => {
        try {
          const created = await postGmsPrompt({
            name: editor.name,
            content: editor.content,
            featureType: editor.featureType,
          });
          setPrompts((prev) => [created, ...prev]);
          setSelectedId(created.id);
          setIsCreating(false);
          setEditor(toEditor(created));
          toast.success("프롬프트를 생성했어요");
        } catch (caughtError) {
          const message =
            caughtError instanceof ApiError
              ? caughtError.message
              : "생성에 실패했어요";
          toast.error(message);
        }
      });
      return;
    }

    if (selectedId === null) return;
    const targetId = selectedId;
    startMutationTransition(async () => {
      try {
        const updated = await patchGmsPrompt(targetId, {
          name: editor.name,
          content: editor.content,
          featureType: editor.featureType,
        });
        setPrompts((prev) =>
          prev.map((prompt) => (prompt.id === targetId ? updated : prompt)),
        );
        setEditor(toEditor(updated));
        toast.success("프롬프트를 저장했어요");
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : "저장에 실패했어요";
        toast.error(message);
      }
    });
  };

  const remove = () => {
    if (isMutating || isCreating || selectedId === null) return;
    if (
      typeof window !== "undefined" &&
      !window.confirm("이 프롬프트를 삭제할까요?")
    )
      return;

    const targetId = selectedId;
    startMutationTransition(async () => {
      try {
        await deleteGmsPrompt(targetId);
        const remaining = prompts.filter((prompt) => prompt.id !== targetId);
        setPrompts(remaining);
        if (remaining.length > 0) {
          setSelectedId(remaining[0].id);
          setEditor(toEditor(remaining[0]));
        } else {
          setSelectedId(null);
          setEditor(EMPTY_EDITOR);
        }
        toast.success("프롬프트를 삭제했어요");
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : "삭제에 실패했어요";
        toast.error(message);
      }
    });
  };

  return {
    isLoading,
    loadError,
    filteredPrompts,
    featureFilter,
    setFeatureFilter,
    selectedId,
    isCreating,
    editor,
    setEditorField,
    isDirty,
    select,
    startCreate,
    cancelCreate,
    save,
    remove,
    isMutating,
  };
}
