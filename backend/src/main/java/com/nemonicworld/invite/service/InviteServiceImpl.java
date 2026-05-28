package com.nemonicworld.invite.service;

import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.service.join.InviteJoinUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 초대코드 참여 요청을 처리하는 서비스 구현체입니다.
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private final InviteJoinUseCase inviteJoinUseCase;

    @Override
    public InviteJoinResponse joinByInviteCode(String inviteCode, String userUuidValue) {
        return inviteJoinUseCase.joinByInviteCode(inviteCode, userUuidValue);
    }
}
