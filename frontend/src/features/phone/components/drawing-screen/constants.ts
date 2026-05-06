import {
  phoneDrawingPrint,
  phoneDrawingSave,
} from '@/shared/assets'
import { PHONE_DRAWING_LAYOUT } from '../../constants'

export const DRAWING_ACTION_BUTTONS = [
  {
    action: 'save',
    icon: phoneDrawingSave,
    iconClassName: 'h-[42%] w-[16.3%]',
    label: '갤러리에 저장',
    style: PHONE_DRAWING_LAYOUT.saveButton,
  },
  {
    action: 'print',
    icon: phoneDrawingPrint,
    iconClassName: 'h-[42%] w-[15.9%]',
    label: '네모닉 출력',
    style: PHONE_DRAWING_LAYOUT.printButton,
  },
] as const
