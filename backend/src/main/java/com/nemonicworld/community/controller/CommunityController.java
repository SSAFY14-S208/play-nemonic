package com.nemonicworld.community.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.community.dto.CommunityDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community")
public class CommunityController {

    @GetMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunityDetailResponse>> getCommunity(@PathVariable Long communityId) {
        CommunityDetailResponse response = new CommunityDetailResponse(communityId, "샘플 커뮤니티 제목",
            "공통 API 응답 포맷 예시입니다.");

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success("커뮤니티 조회 성공", response));
    }
}
