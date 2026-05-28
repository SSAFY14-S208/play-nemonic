package com.nemonicworld.gallery.service;

import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.service.gallery.GalleryCommandUseCase;
import com.nemonicworld.gallery.service.gallery.GalleryQueryUseCase;
import org.springframework.stereotype.Service;

/**
 * UUID 기반 내 갤러리 조회 유스케이스를 처리하는 서비스입니다.
 */
@Service
public class GalleryServiceImpl implements GalleryService {

    private final GalleryQueryUseCase galleryQueryUseCase;
    private final GalleryCommandUseCase galleryCommandUseCase;

    public GalleryServiceImpl(GalleryQueryUseCase galleryQueryUseCase, GalleryCommandUseCase galleryCommandUseCase) {
        this.galleryQueryUseCase = galleryQueryUseCase;
        this.galleryCommandUseCase = galleryCommandUseCase;
    }

    /**
     * 존재하는 익명 사용자의 보관 결과물을 최신순으로 조회합니다.
     */
    @Override
    public GalleryListResponse getMyGallery(String userUuidValue, String pageValue, String sizeValue) {
        return galleryQueryUseCase.getMyGallery(userUuidValue, pageValue, sizeValue);
    }

    /**
     * 존재하는 익명 사용자의 보관 결과물 한 건을 상세 조회합니다.
     */
    @Override
    public GalleryDetailResponse getMyGalleryItemDetail(String userUuidValue, String galleryIdValue) {
        return galleryQueryUseCase.getMyGalleryItemDetail(userUuidValue, galleryIdValue);
    }

    /**
     * 원본 artifact는 보존하고 갤러리 보관 관계만 soft delete 처리합니다.
     */
    @Override
    public GalleryDeleteResponse deleteMyGalleryItem(String userUuidValue, String galleryIdValue) {
        return galleryCommandUseCase.deleteMyGalleryItem(userUuidValue, galleryIdValue);
    }
}
