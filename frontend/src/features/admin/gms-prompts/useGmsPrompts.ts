'use client'

import { useEffect, useMemo, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  deleteGmsPrompt,
  getGmsPrompt,
  getGmsPromptList,
  patchGmsPrompt,
  postGmsPrompt,
  postGmsPromptActivate,
  postGmsPromptPreview,
  postGmsPromptTest,
} from '@/shared/apis'
import type {
  FortuneCreateRequest,
  GmsFeatureType,
  GmsPromptPreviewResponse,
  GmsPromptResponse,
} from '@/shared/types'

export interface GmsPromptEditorState {
  name: string
  content: string
  featureType: GmsFeatureType
}

export type FeatureFilter = GmsFeatureType | 'ALL'

const EMPTY_EDITOR: GmsPromptEditorState = {
  name: '',
  content: '',
  featureType: 'fortune',
}

const toEditor = (prompt: GmsPromptResponse): GmsPromptEditorState => ({
  name: prompt.name,
  content: prompt.content,
  featureType: prompt.featureType,
})

function applyActivatedPrompt(
  prompts: GmsPromptResponse[],
  activatedPrompt: GmsPromptResponse,
) {
  return prompts.map((prompt) => {
    if (prompt.id === activatedPrompt.id) return activatedPrompt
    if (prompt.featureType !== activatedPrompt.featureType || !prompt.isActive) {
      return prompt
    }

    return {
      ...prompt,
      isActive: false,
      status: 'not_active' as const,
      activatedAt: null,
      activatedBy: null,
    }
  })
}

function getErrorMessage(caughtError: unknown, fallbackMessage: string) {
  return caughtError instanceof ApiError ? caughtError.message : fallbackMessage
}

export function useGmsPrompts() {
  const [prompts, setPrompts] = useState<GmsPromptResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [featureFilter, setFeatureFilter] = useState<FeatureFilter>('ALL')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [editor, setEditor] = useState<GmsPromptEditorState>(EMPTY_EDITOR)
  const [isTesting, setIsTesting] = useState(false)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const response = await getGmsPromptList({
          page: 0,
          size: 50,
          status: 'all',
        })
        if (cancelled) return
        setPrompts(response.items)
        setLoadError(null)
        if (response.items.length > 0) {
          setSelectedId(response.items[0].id)
          setEditor(toEditor(response.items[0]))
        }
      } catch (caughtError) {
        if (cancelled) return
        setLoadError(
          getErrorMessage(caughtError, '프롬프트 목록을 불러오지 못했어요.'),
        )
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const filteredPrompts = useMemo(() => {
    if (featureFilter === 'ALL') return prompts
    return prompts.filter((prompt) => prompt.featureType === featureFilter)
  }, [prompts, featureFilter])

  const selected = useMemo(
    () => prompts.find((prompt) => prompt.id === selectedId) ?? null,
    [prompts, selectedId],
  )

  const isDirty = useMemo(() => {
    if (isCreating) {
      return Boolean(editor.name.trim() || editor.content.trim())
    }
    if (!selected) return false
    return (
      editor.name !== selected.name ||
      editor.content !== selected.content ||
      editor.featureType !== selected.featureType
    )
  }, [editor, selected, isCreating])

  const select = (id: number) => {
    const target = prompts.find((prompt) => prompt.id === id)
    if (!target) return
    setSelectedId(id)
    setIsCreating(false)
    setEditor(toEditor(target))

    void (async () => {
      try {
        const detail = await getGmsPrompt(id)
        setPrompts((prev) =>
          prev.map((prompt) => (prompt.id === id ? detail : prompt)),
        )
        setEditor((prev) => {
          const editorMatchesCached =
            prev.name === target.name &&
            prev.content === target.content &&
            prev.featureType === target.featureType
          return editorMatchesCached ? toEditor(detail) : prev
        })
      } catch {
        // 상세 조회 실패 시 목록 캐시를 그대로 사용한다.
      }
    })()
  }

  const startCreate = () => {
    setIsCreating(true)
    setSelectedId(null)
    setEditor(EMPTY_EDITOR)
  }

  const cancelCreate = () => {
    setIsCreating(false)
    if (prompts.length > 0) {
      const first = prompts[0]
      setSelectedId(first.id)
      setEditor(toEditor(first))
    } else {
      setEditor(EMPTY_EDITOR)
    }
  }

  const setEditorField = <K extends keyof GmsPromptEditorState>(
    field: K,
    value: GmsPromptEditorState[K],
  ) => {
    setEditor((prev) => ({ ...prev, [field]: value }))
  }

  const save = () => {
    if (isMutating || !isDirty) return

    if (isCreating) {
      if (!editor.name.trim() || !editor.content.trim()) {
        toast.error('이름과 본문을 입력해 주세요.')
        return
      }
      startMutationTransition(async () => {
        try {
          const created = await postGmsPrompt({
            name: editor.name.trim(),
            content: editor.content,
            featureType: editor.featureType,
          })
          setPrompts((prev) => [created, ...prev])
          setSelectedId(created.id)
          setIsCreating(false)
          setEditor(toEditor(created))
          toast.success('프롬프트를 생성했어요.')
        } catch (caughtError) {
          toast.error(getErrorMessage(caughtError, '생성에 실패했어요.'))
        }
      })
      return
    }

    if (selectedId === null) return
    const targetId = selectedId
    startMutationTransition(async () => {
      try {
        const updated = await patchGmsPrompt(targetId, {
          name: editor.name.trim(),
          content: editor.content,
          featureType: editor.featureType,
        })
        setPrompts((prev) =>
          prev.map((prompt) => (prompt.id === targetId ? updated : prompt)),
        )
        setEditor(toEditor(updated))
        toast.success('프롬프트를 저장했어요.')
      } catch (caughtError) {
        toast.error(getErrorMessage(caughtError, '저장에 실패했어요.'))
      }
    })
  }

  const activate = () => {
    if (isMutating || isCreating || selectedId === null) return
    const target = prompts.find((prompt) => prompt.id === selectedId)
    if (!target || target.isActive) return
    if (
      typeof window !== 'undefined' &&
      !window.confirm(`'${target.name}' 프롬프트를 활성화할까요?`)
    ) {
      return
    }

    const targetId = selectedId
    startMutationTransition(async () => {
      try {
        const activated = await postGmsPromptActivate(targetId)
        setPrompts((prev) => applyActivatedPrompt(prev, activated))
        toast.success('프롬프트를 활성화했어요.')
      } catch (caughtError) {
        toast.error(getErrorMessage(caughtError, '활성화에 실패했어요.'))
      }
    })
  }

  const runTest = async (
    sampleSaju: FortuneCreateRequest,
  ): Promise<GmsPromptPreviewResponse | null> => {
    if (isTesting) return null
    if (editor.featureType !== 'fortune') {
      toast.error('오늘의 운세 프롬프트만 테스트할 수 있어요.')
      return null
    }
    if (!editor.content.trim()) {
      toast.error('테스트할 프롬프트 본문을 입력해 주세요.')
      return null
    }

    setIsTesting(true)
    try {
      if (!isCreating && selectedId !== null && !isDirty) {
        return await postGmsPromptTest(selectedId, { sampleSaju })
      }

      return await postGmsPromptPreview({
        featureType: editor.featureType,
        content: editor.content,
        sampleSaju,
      })
    } catch (caughtError) {
      toast.error(getErrorMessage(caughtError, '테스트에 실패했어요.'))
      return null
    } finally {
      setIsTesting(false)
    }
  }

  const remove = () => {
    if (isMutating || isCreating || selectedId === null) return
    if (
      typeof window !== 'undefined' &&
      !window.confirm('이 프롬프트를 삭제할까요?')
    ) {
      return
    }

    const targetId = selectedId
    startMutationTransition(async () => {
      try {
        await deleteGmsPrompt(targetId)
        const remaining = prompts.filter((prompt) => prompt.id !== targetId)
        setPrompts(remaining)
        if (remaining.length > 0) {
          setSelectedId(remaining[0].id)
          setEditor(toEditor(remaining[0]))
        } else {
          setSelectedId(null)
          setEditor(EMPTY_EDITOR)
        }
        toast.success('프롬프트를 삭제했어요.')
      } catch (caughtError) {
        toast.error(getErrorMessage(caughtError, '삭제에 실패했어요.'))
      }
    })
  }

  return {
    isLoading,
    loadError,
    filteredPrompts,
    featureFilter,
    setFeatureFilter,
    selected,
    selectedId,
    isCreating,
    editor,
    setEditorField,
    isDirty,
    select,
    startCreate,
    cancelCreate,
    save,
    activate,
    runTest,
    remove,
    isMutating,
    isTesting,
  }
}
