export const resultPrintStageStyles = {
  stage: 'fprs-stage',
  scene: 'fprs-scene',
  background: 'fprs-background',
  topFurniture: 'fprs-top-furniture',
  leftFurniture: 'fprs-left-furniture',
  boardLayer: 'fprs-board-layer',
  boardImage: 'fprs-board-image',
  attachedPaperFrame: 'fprs-attached-paper-frame',
  printerLayer: 'fprs-printer-layer',
  deviceImage: 'fprs-device-image',
  slotPrintMask: 'fprs-slot-print-mask',
  outputSlot: 'fprs-output-slot',
  participantPanel: 'fprs-participant-panel',
  participantPanelImage: 'fprs-participant-panel-image',
  participantPanelHeader: 'fprs-participant-panel-header',
  participantListViewport: 'fprs-participant-list-viewport',
  participantListItem: 'fprs-participant-list-item',
  participantSelectButton: 'fprs-participant-select-button',
  skipPlaybackButton: 'fprs-skip-playback-button',
  skipPlaybackButtonImage: 'fprs-skip-playback-button-image',
  skipPlaybackButtonText: 'fprs-skip-playback-button-text',
  frameArtistBadge: 'fprs-frame-artist-badge',
  frameArtistBadgeImage: 'fprs-frame-artist-badge-image',
  frameArtistBadgeLabel: 'fprs-frame-artist-badge-label',
  frameArtistBadgeName: 'fprs-frame-artist-badge-name',
} as const

export const DEFAULT_ACCENT_COLORS = [
  '#f58c97',
  '#7ec6ad',
  '#f3c66f',
  '#96a8ee',
  '#c99be8',
  '#ef9a72',
] as const

export const RESULT_STAGE_BACKGROUND_IMAGE_SRC =
  '/images/flipbook-result/figma-node-2826-background-render.png'
export const RESULT_STAGE_BACKGROUND_IMAGE_WIDTH = 1920
export const RESULT_STAGE_BACKGROUND_IMAGE_HEIGHT = 1080
export const FURNITURE_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-furniture-left.png'
export const FURNITURE_IMAGE_WIDTH = 1536
export const FURNITURE_IMAGE_HEIGHT = 1024
export const BOARD_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-board-v2.png'
export const BOARD_IMAGE_WIDTH = 1113
export const BOARD_IMAGE_HEIGHT = 744
export const NEMONIC_DEVICE_IMAGE_SRC = '/images/flipbook-result/attached-nemonic-device-v3-hq.png'
export const NEMONIC_DEVICE_IMAGE_WIDTH = 1551
export const NEMONIC_DEVICE_IMAGE_HEIGHT = 1035
export const NEMONIC_OUTPUT_SLOT_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-output-slot.svg'
export const PARTICIPANT_PANEL_IMAGE_SRC = '/images/flipbook-result/participant-panel-v2.png'
export const SKIP_BUTTON_IMAGE_SRC = '/images/flipbook-result/skip-button.png'
export const ARTIST_BADGE_IMAGE_SRC = '/images/flipbook-result/artist-badge.png'
export const PRINT_RISE_DURATION_RATIO = 0.5
export const PRINT_AFTER_RISE_PAUSE_RATIO = 0.25
export const PRINT_START_SOUND_VOLUME = 0.36
export const PRINT_COMPLETE_SOUND_VOLUME = 0.42
export const PRINT_START_SOUND_OFFSET_SECONDS = 0.2
export const PRINT_COMPLETE_SOUND_OFFSET_SECONDS = 0.08
export const PRINT_COMPLETE_SOUND_LEAD_MS = 120
export const PRINTED_PAPER_SHADOW_CLASS =
  'shadow-[0_2px_0_rgba(120,74,35,0.08),0_8px_18px_rgba(72,43,18,0.22),0_18px_36px_rgba(72,43,18,0.18)]'
