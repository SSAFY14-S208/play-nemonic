'use client'

import { useState } from 'react'
import { postInquiry, ApiError } from '@/shared/apis'

import type { CsInquiryType } from '@/shared/types'
import { PHONE_INQUIRY_TYPE_OPTIONS } from '../constants'
import { usePhoneStore } from '..'

interface InquiryFieldErrors {
  title?: string
  content?: string
  email?: string
}

function validateEmail(value: string): boolean {
  if (!value) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

export function usePhoneInquiry() {
  const goHome = usePhoneStore((state) => state.goHome)
  const setToast = usePhoneStore((state) => state.setToast)

  const [type, setType] = useState<CsInquiryType>(
    PHONE_INQUIRY_TYPE_OPTIONS[0].value,
  )
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [email, setEmail] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<InquiryFieldErrors>({})

  const clearFieldError = (field: keyof InquiryFieldErrors) => {
    setFieldErrors((prev) => {
      if (!prev[field]) return prev
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const validate = (): boolean => {
    const errors: InquiryFieldErrors = {}
    if (!title.trim()) errors.title = '제목을 입력해주세요.'
    if (!content.trim()) errors.content = '내용을 입력해주세요.'
    if (email && !validateEmail(email.trim())) {
      errors.email = '올바른 이메일 형식을 입력해주세요.'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const submit = async () => {
    if (isSubmitting) return
    if (!validate()) return

    setIsSubmitting(true)
    try {
      await postInquiry({
        type,
        title: title.trim(),
        content: content.trim(),
        email: email.trim() || null,
      })
      setToast('문의가 접수되었어요.')
      goHome()
    } catch (error) {
      if (error instanceof ApiError) {
        setToast(error.message || '문의 접수에 실패했어요.')
      } else {
        setToast('문의 접수에 실패했어요.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return {
    type,
    setType,
    title,
    setTitle,
    content,
    setContent,
    email,
    setEmail,
    isSubmitting,
    fieldErrors,
    clearFieldError,
    submit,
  }
}
