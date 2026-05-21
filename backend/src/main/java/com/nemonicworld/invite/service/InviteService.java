package com.nemonicworld.invite.service;

import com.nemonicworld.invite.dto.response.InviteJoinResponse;

public interface InviteService {

    InviteJoinResponse joinByInviteCode(String inviteCode, String userUuidValue);
}
