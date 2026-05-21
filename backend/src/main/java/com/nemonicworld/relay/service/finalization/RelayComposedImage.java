package com.nemonicworld.relay.service.finalization;

/**
 * 최종 원본 이미지와 썸네일 PNG 바이트를 함께 전달합니다.
 */
public record RelayComposedImage(byte[] originalPng, byte[] thumbnailPng) {
}
