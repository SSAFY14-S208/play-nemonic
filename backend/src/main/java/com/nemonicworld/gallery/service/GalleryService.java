package com.nemonicworld.gallery.service;

import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;

/**
 * 익명 사용자 기준 갤러리 조회, 상세 조회, 삭제 유스케이스를 정의합니다.
 */
public interface GalleryService {

    GalleryListResponse getMyGallery(String userUuidValue, String pageValue, String sizeValue);

    GalleryDetailResponse getMyGalleryItemDetail(String userUuidValue, String galleryIdValue);

    GalleryDeleteResponse deleteMyGalleryItem(String userUuidValue, String galleryIdValue);

}
