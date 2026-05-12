"use client";

import { useEffect, useMemo, useState, useTransition } from "react";
import { toast } from "sonner";

import {
  ApiError,
  getSystemParameters,
  patchSystemParameters,
} from "@/shared/apis";
import type {
  SystemParameterBulkUpdateRequest,
  SystemParameterResponse,
  SystemParameterValue,
} from "@/shared/types";

import { CONTENT_PARAMETERS } from "./constants";

interface ParameterServerRecord {
  id: number;
  serverValue: SystemParameterValue;
  updatedAt: string;
}

export interface UseContentParametersReturn {
  isLoading: boolean;
  loadError: string | null;
  /** backendKey → 서버 진실값 (저장된 마지막 상태). */
  parametersByKey: Map<string, ParameterServerRecord>;
  /** backendKey → 사용자 편집 중인 value 객체. */
  draft: Record<string, SystemParameterValue>;
  hasChanges: boolean;
  setDraftField: <K extends keyof SystemParameterValue>(
    backendKey: string,
    field: K,
    value: SystemParameterValue[K],
  ) => void;
  /**
   * enum 파라미터(최대/최소 시간)의 양 끝값을 받아 3개 허용값과 default를 자동 산출.
   *   allowed = [min, floor((min + max) / 2), max]
   *   default = min (allowed[0])
   * 한 번에 한 endpoint만 전달하면 나머지는 prev 상태에서 읽어 합성.
   */
  setEnumBounds: (
    backendKey: string,
    bounds: { min?: number; max?: number },
  ) => void;
  resetDraft: () => void;
  save: () => void;
  isSaving: boolean;
}

/**
 * 백엔드의 `value`는 객체 또는 raw 문자열(예: "50")로 옴.
 * 항상 객체 형태(`SystemParameterValue`)로 정규화해 store/draft를 단일 shape으로 유지한다.
 * raw 문자열은 단일값 파라미터가 수정된 직후에 떨어지는 케이스라 `value` 필드로 매핑.
 */
function normalizeValue(
  raw: SystemParameterValue | string,
): SystemParameterValue {
  if (typeof raw === "string") {
    const numeric = Number(raw);
    return Number.isFinite(numeric) ? { value: numeric } : {};
  }
  return raw;
}

function toRecord(parameters: SystemParameterResponse[]) {
  const byKey = new Map<string, ParameterServerRecord>();
  const draft: Record<string, SystemParameterValue> = {};
  for (const parameter of parameters) {
    const normalized = normalizeValue(parameter.value);
    byKey.set(parameter.key, {
      id: parameter.id,
      serverValue: normalized,
      updatedAt: parameter.updatedAt,
    });
    draft[parameter.key] = { ...normalized };
  }
  return { byKey, draft };
}

export function useContentParameters(): UseContentParametersReturn {
  const [parametersByKey, setParametersByKey] = useState<
    Map<string, ParameterServerRecord>
  >(new Map());
  const [draft, setDraft] = useState<Record<string, SystemParameterValue>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, startSavingTransition] = useTransition();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await getSystemParameters();
        if (cancelled) return;
        const { byKey, draft: nextDraft } = toRecord(response.items);
        setParametersByKey(byKey);
        setDraft(nextDraft);
        setLoadError(null);
      } catch (caughtError) {
        if (cancelled) return;
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : "파라미터를 불러오지 못했어요";
        setLoadError(message);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const hasChanges = useMemo(() => {
    for (const [key, value] of Object.entries(draft)) {
      const server = parametersByKey.get(key);
      if (!server) continue;
      if (JSON.stringify(server.serverValue) !== JSON.stringify(value))
        return true;
    }
    return false;
  }, [draft, parametersByKey]);

  const setDraftField: UseContentParametersReturn["setDraftField"] = (
    backendKey,
    field,
    value,
  ) => {
    setDraft((prev) => ({
      ...prev,
      [backendKey]: { ...(prev[backendKey] ?? {}), [field]: value },
    }));
  };

  const setEnumBounds: UseContentParametersReturn["setEnumBounds"] = (
    backendKey,
    bounds,
  ) => {
    setDraft((prev) => {
      const current = prev[backendKey] ?? {};
      const previousAllowed = current.allowed;
      const previousMin = previousAllowed?.[0] ?? 0;
      const previousMax =
        previousAllowed && previousAllowed.length > 0
          ? previousAllowed[previousAllowed.length - 1]
          : 0;
      const min = bounds.min ?? previousMin;
      const max = bounds.max ?? previousMax;
      const mid = Math.floor((min + max) / 2);
      return {
        ...prev,
        [backendKey]: {
          ...current,
          allowed: [min, mid, max],
          default: min,
        },
      };
    });
  };

  const resetDraft = () => {
    const next: Record<string, SystemParameterValue> = {};
    for (const [key, record] of parametersByKey) {
      next[key] = { ...record.serverValue };
    }
    setDraft(next);
  };

  const save = () => {
    if (isSaving || !hasChanges) return;

    // 변경분만 필드 기반(camelCase patchKey)으로 모은다.
    // 각 value는 `unit`/`description`까지 포함한 full object — 누락 시 백엔드가
    // 단순화해 string으로 저장하므로 메타에서 강제로 채워 넣는다.
    const payload: SystemParameterBulkUpdateRequest = {};
    for (const meta of CONTENT_PARAMETERS) {
      const server = parametersByKey.get(meta.backendKey);
      if (!server) continue;
      const draftValue = draft[meta.backendKey];
      if (!draftValue) continue;
      if (JSON.stringify(server.serverValue) === JSON.stringify(draftValue))
        continue;

      payload[meta.patchKey] = {
        ...draftValue,
        unit: draftValue.unit ?? meta.unit,
        description: draftValue.description ?? meta.description,
      };
    }

    if (Object.keys(payload).length === 0) return;

    startSavingTransition(async () => {
      try {
        const response = await patchSystemParameters(payload);
        // PATCH 응답은 변경된 항목만 돌아오므로 merge — 미변경 항목의 state는 유지.
        const { byKey: updatedByKey, draft: updatedDraft } = toRecord(
          response.items,
        );
        setParametersByKey((prev) => {
          const next = new Map(prev);
          for (const [key, record] of updatedByKey) {
            next.set(key, record);
          }
          return next;
        });
        setDraft((prev) => ({ ...prev, ...updatedDraft }));
        toast.success("파라미터를 저장했어요");
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : "저장에 실패했어요";
        toast.error(message);
      }
    });
  };

  return {
    isLoading,
    loadError,
    parametersByKey,
    draft,
    hasChanges,
    setDraftField,
    setEnumBounds,
    resetDraft,
    save,
    isSaving,
  };
}
