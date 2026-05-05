package com.nemonicworld.invite.service;

import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.user.entity.AppUser;

/**
 * 부스 타입별 초대 입장 처리를 위한 확장 지점입니다.
 */
public interface InviteJoinHandler {

    /**
     * 정규화된 부스 타입을 이 핸들러가 처리할 수 있는지 확인합니다.
     */
    boolean supports(String boothType);

    /**
     * 초대 메타데이터가 가리키는 부스 방에 사용자를 입장시킵니다.
     */
    InviteJoinResponse join(InviteMetadata invite, AppUser user);
}
