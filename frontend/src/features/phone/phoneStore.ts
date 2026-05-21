'use client'

import { create } from 'zustand'
import {
  ApiError,
  deleteGallery,
  getAnonymousProfile,
  getArtifactDownload,
  getGallery,
  getGalleryList,
  patchAnonymousNickname,
} from '@/shared/apis'
import {
  useCanvasPauseStore,
  useUserStore,
} from '@/shared/stores'
import type {
  AnonymousUserProfileResponse,
  GalleryDetailResponse,
  PhoneDrawingSaveResponse,
} from '@/shared/types'
import {
  downloadBlob,
  inferImageExtensionFromBlob,
  sanitizeDownloadFilename,
} from '@/shared/utils'
import type { PhoneGalleryItem, PhoneScreenKey } from './types'
import { mapGalleryItemResponseToPhoneItem } from './utils'

const GALLERY_PAGE_SIZE = 15

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

export interface PhoneProfile {
  nickname: string | null
  birthday: string | null
  birthtime: string | null
  isLunar: boolean | null
}

interface PhoneStore {
  // UI state
  activeScreen: PhoneScreenKey
  galleryItems: PhoneGalleryItem[]
  isPhoneOpen: boolean
  selectedGalleryItemId: string | null
  toastMessage: string | null

  // profile
  profile: PhoneProfile | null
  profileStatus: AsyncStatus
  profileError: string | null
  nicknameUpdateStatus: AsyncStatus
  nicknameFieldError: string | null

  // gallery list
  galleryPage: number
  galleryHasNext: boolean
  galleryTotal: number
  galleryStatus: AsyncStatus
  galleryError: string | null
  galleryLoadingMore: boolean

  // gallery detail
  galleryDetail: GalleryDetailResponse | null
  galleryDetailStatus: AsyncStatus
  galleryDetailError: string | null

  // gallery download — 진행 중인 항목 ID. 동시 다운로드 방지 + 버튼 로딩 상태에
  // 사용. null이면 진행 중인 다운로드 없음.
  galleryDownloadingId: string | null

  // drawing save
  isSavingDrawing: boolean

  // basic UI actions
  closeGalleryItem: () => void
  closePhone: () => void
  dismissToast: () => void
  setToast: (message: string) => void
  goHome: () => void
  openPhone: () => void
  selectGalleryItem: (itemId: string) => void
  showDrawing: () => void
  showGallery: () => void
  showInquiry: () => void
  showTeleport: () => void

  // profile actions
  loadProfile: () => Promise<void>
  updateNickname: (nickname: string) => Promise<boolean>
  clearNicknameFieldError: () => void

  // gallery actions
  loadGallery: (opts?: { force?: boolean }) => Promise<void>
  loadMoreGallery: () => Promise<void>
  loadGalleryDetail: (galleryId: string) => Promise<void>
  clearGalleryDetail: () => void
  deleteGalleryItem: (galleryId: string) => Promise<void>
  downloadGalleryItem: (galleryId: string) => Promise<void>

  // drawing actions
  setSavingDrawing: (isSaving: boolean) => void
  addDrawingArtifact: (
    params:
      | {
          action: 'save'
          imageDataUrl: string
          saveResponse: PhoneDrawingSaveResponse
        }
      | {
          action: 'print'
          imageDataUrl: string
          saveResponse: PhoneDrawingSaveResponse
        },
  ) => void
}

function toErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof Error) return error.message || fallback
  return fallback
}

function toProfile(response: AnonymousUserProfileResponse): PhoneProfile {
  return {
    nickname: response.nickname,
    birthday: response.birthday,
    birthtime: response.birthtime,
    isLunar: response.isLunar,
  }
}

function createSavedDrawingItem(
  saveResponse: PhoneDrawingSaveResponse,
  imageDataUrl: string,
): PhoneGalleryItem {
  return {
    id: saveResponse.galleryId,
    artifactId: saveResponse.artifactId,
    kind: 'phone',
    title: '내가 그린 메모',
    createdAtLabel: '방금 전',
    badgeLabel: 'NEW',
    // 서버 thumbnail이 있으면 그걸, 없으면 클라가 만든 dataURL을 폴백으로
    imageDataUrl: saveResponse.thumbnailUrl || imageDataUrl,
    isNew: true,
  }
}

export const usePhoneStore = create<PhoneStore>((set, get) => ({
  activeScreen: 'home',
  galleryItems: [],
  isPhoneOpen: false,
  selectedGalleryItemId: null,
  toastMessage: null,

  profile: null,
  profileStatus: 'idle',
  profileError: null,
  nicknameUpdateStatus: 'idle',
  nicknameFieldError: null,

  galleryPage: 0,
  galleryHasNext: false,
  galleryTotal: 0,
  galleryStatus: 'idle',
  galleryError: null,
  galleryLoadingMore: false,

  galleryDetail: null,
  galleryDetailStatus: 'idle',
  galleryDetailError: null,

  galleryDownloadingId: null,

  isSavingDrawing: false,

  closeGalleryItem: () =>
    set({
      selectedGalleryItemId: null,
      galleryDetail: null,
      galleryDetailStatus: 'idle',
      galleryDetailError: null,
    }),
  closePhone: () => {
    useCanvasPauseStore.getState().setPaused(false)
    set({
      activeScreen: 'home',
      isPhoneOpen: false,
      selectedGalleryItemId: null,
      toastMessage: null,
      galleryDetail: null,
      galleryDetailStatus: 'idle',
      galleryDetailError: null,
    })
  },
  dismissToast: () => set({ toastMessage: null }),
  setToast: (toastMessage) => set({ toastMessage }),
  goHome: () => set({ activeScreen: 'home', selectedGalleryItemId: null }),
  openPhone: () => {
    useCanvasPauseStore.getState().setPaused(true)
    set({
      activeScreen: 'home',
      isPhoneOpen: true,
      selectedGalleryItemId: null,
    })
  },
  selectGalleryItem: (itemId) => set({ selectedGalleryItemId: itemId }),
  showDrawing: () => set({ activeScreen: 'drawing', selectedGalleryItemId: null }),
  showGallery: () => set({ activeScreen: 'gallery', selectedGalleryItemId: null }),
  showInquiry: () => set({ activeScreen: 'inquiry', selectedGalleryItemId: null }),
  showTeleport: () => set({ activeScreen: 'teleport', selectedGalleryItemId: null }),

  loadProfile: async () => {
    const { profileStatus } = get()
    if (profileStatus === 'loading') return
    if (!useUserStore.getState().userUuid) return
    set({ profileStatus: 'loading', profileError: null })
    try {
      const response = await getAnonymousProfile()
      set({ profile: toProfile(response), profileStatus: 'success' })
    } catch (error) {
      set({
        profileStatus: 'error',
        profileError: toErrorMessage(error, '프로필을 불러올 수 없어요.'),
      })
    }
  },

  updateNickname: async (nickname) => {
    const trimmed = nickname.trim()
    if (!trimmed) {
      set({ nicknameFieldError: '닉네임을 입력해주세요.' })
      return false
    }
    const previousProfile = get().profile
    set({
      nicknameUpdateStatus: 'loading',
      nicknameFieldError: null,
      profile: previousProfile
        ? { ...previousProfile, nickname: trimmed }
        : previousProfile,
    })
    try {
      const response = await patchAnonymousNickname({ nickname: trimmed })
      set((state) => ({
        nicknameUpdateStatus: 'success',
        profile: state.profile
          ? { ...state.profile, nickname: response.nickname }
          : state.profile,
      }))
      const userStore = useUserStore.getState()
      if (userStore.userUuid) {
        userStore.setUser(userStore.userUuid, response.nickname)
      }
      return true
    } catch (error) {
      // 백엔드는 닉네임 검증 실패 시 errors 맵 없이 message만 보낸다
      // (UserServiceImpl.validateNickname → BadRequestException). errors 우선,
      // 없으면 message를 인풋 아래에 표시한다.
      const fieldError =
        error instanceof ApiError
          ? error.errors?.nickname ?? error.message ?? null
          : null
      set({
        nicknameUpdateStatus: 'error',
        nicknameFieldError: fieldError,
        profile: previousProfile,
        toastMessage: fieldError
          ? null
          : toErrorMessage(error, '닉네임을 변경할 수 없어요.'),
      })
      return false
    }
  },

  clearNicknameFieldError: () => set({ nicknameFieldError: null }),

  loadGallery: async (opts) => {
    const state = get()
    if (state.galleryStatus === 'loading') return
    if (!opts?.force && state.galleryStatus === 'success') return
    if (!useUserStore.getState().userUuid) return
    set({ galleryStatus: 'loading', galleryError: null })
    try {
      const response = await getGalleryList({ page: 0, size: GALLERY_PAGE_SIZE })
      set({
        galleryItems: response.items.map(mapGalleryItemResponseToPhoneItem),
        galleryPage: response.page,
        galleryHasNext: response.hasNext,
        galleryTotal: response.totalElements,
        galleryStatus: 'success',
      })
    } catch (error) {
      set({
        galleryStatus: 'error',
        galleryError: toErrorMessage(error, '갤러리를 불러올 수 없어요.'),
      })
    }
  },

  loadMoreGallery: async () => {
    const state = get()
    if (state.galleryLoadingMore || !state.galleryHasNext) return
    if (state.galleryStatus !== 'success') return
    set({ galleryLoadingMore: true })
    try {
      const nextPage = state.galleryPage + 1
      const response = await getGalleryList({
        page: nextPage,
        size: GALLERY_PAGE_SIZE,
      })
      set((current) => ({
        galleryItems: [
          ...current.galleryItems,
          ...response.items.map(mapGalleryItemResponseToPhoneItem),
        ],
        galleryPage: response.page,
        galleryHasNext: response.hasNext,
        galleryTotal: response.totalElements,
        galleryLoadingMore: false,
      }))
    } catch (error) {
      set({
        galleryLoadingMore: false,
        toastMessage: toErrorMessage(error, '추가 항목을 불러오지 못했어요.'),
      })
    }
  },

  loadGalleryDetail: async (galleryId) => {
    set({
      galleryDetailStatus: 'loading',
      galleryDetailError: null,
      galleryDetail: null,
    })
    try {
      const detail = await getGallery(galleryId)
      set({ galleryDetail: detail, galleryDetailStatus: 'success' })
    } catch (error) {
      set({
        galleryDetailStatus: 'error',
        galleryDetailError: toErrorMessage(error, '항목을 불러올 수 없어요.'),
      })
    }
  },

  clearGalleryDetail: () =>
    set({
      galleryDetail: null,
      galleryDetailStatus: 'idle',
      galleryDetailError: null,
    }),

  deleteGalleryItem: async (galleryId) => {
    const state = get()
    const previousItems = state.galleryItems
    const previousTotal = state.galleryTotal
    set({
      galleryItems: previousItems.filter((item) => item.id !== galleryId),
      galleryTotal: Math.max(0, previousTotal - 1),
      selectedGalleryItemId: null,
      galleryDetail: null,
      galleryDetailStatus: 'idle',
      galleryDetailError: null,
    })
    try {
      await deleteGallery(galleryId)
      set({ toastMessage: '갤러리에서 삭제했어요.' })
    } catch (error) {
      set({
        galleryItems: previousItems,
        galleryTotal: previousTotal,
        toastMessage: toErrorMessage(error, '삭제에 실패했어요.'),
      })
    }
  },

  // 갤러리 항목을 사용자 디바이스로 다운로드. GET /artifacts/{artifactId}/download
  // 가 raw Blob을 반환하므로 브라우저 a[download] 트리거로 OS 저장 다이얼로그를
  // 띄운다. artifactId는 갤러리 상세에 있으므로, 시트가 닫혀서 detail이 비어
  // 있는 경우를 대비해 함수 안에서 detail을 재조회해 어디서 호출돼도 동작하도록.
  downloadGalleryItem: async (galleryId) => {
    const state = get()
    if (state.galleryDownloadingId) return
    set({ galleryDownloadingId: galleryId })
    try {
      const cachedDetail = state.galleryDetail
      const detail =
        cachedDetail && cachedDetail.galleryId === galleryId
          ? cachedDetail
          : await getGallery(galleryId)
      const blob = await getArtifactDownload(detail.artifactId)
      const item = state.galleryItems.find((galleryItem) => galleryItem.id === galleryId)
      const baseFilename = sanitizeDownloadFilename(item?.title ?? `gallery-${galleryId}`)
      const extension = inferImageExtensionFromBlob(blob)
      downloadBlob(blob, `${baseFilename}.${extension}`)
      set({ toastMessage: '저장했어요.' })
    } catch (error) {
      set({ toastMessage: toErrorMessage(error, '저장에 실패했어요.') })
    } finally {
      set({ galleryDownloadingId: null })
    }
  },

  setSavingDrawing: (isSavingDrawing) => set({ isSavingDrawing }),

  addDrawingArtifact: (params) => {
    if (params.action === 'print') {
      useCanvasPauseStore.getState().setPaused(false)

      set((state) => ({
        activeScreen: 'home',
        isPhoneOpen: false,
        galleryItems: [
          createSavedDrawingItem(params.saveResponse, params.imageDataUrl),
          ...state.galleryItems,
        ],
        galleryTotal: state.galleryTotal + 1,
        selectedGalleryItemId: null,
        toastMessage: null,
      }))
      void get().loadGallery({ force: true })
      return
    }

    set((state) => ({
      activeScreen: 'gallery',
      galleryItems: [
        createSavedDrawingItem(params.saveResponse, params.imageDataUrl),
        ...state.galleryItems,
      ],
      galleryTotal: state.galleryTotal + 1,
      selectedGalleryItemId: null,
      toastMessage: '갤러리에 저장했어요.',
    }))
    void get().loadGallery({ force: true })
  },
}))
