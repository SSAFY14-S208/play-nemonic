package com.nemonicworld.gallery.service;

import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;

/**
 * 익명 사용자 기준 갤러리 조회, 상세 조회, 삭제 유스케이스를 정의합니다.
 */
public interface GalleryService {

    /**
     * 익명 사용자의 활성 갤러리 항목 목록을 페이지 단위로 조회합니다.
     */
    GalleryListResponse getMyGallery(String userUuidValue, String pageValue, String sizeValue);

    /**
     * 익명 사용자가 소유한 활성 갤러리 항목 한 건의 상세 정보를 조회합니다.
     */
    GalleryDetailResponse getMyGalleryItemDetail(String userUuidValue, String galleryIdValue);

    /**
     * 익명 사용자의 갤러리 보관 관계만 삭제 처리합니다.
     */
    GalleryDeleteResponse deleteMyGalleryItem(String userUuidValue, String galleryIdValue);

}
