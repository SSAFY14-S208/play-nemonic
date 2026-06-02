package com.nemonicworld.invite.service.join;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.invite.service.InviteJoinHandler;
import com.nemonicworld.invite.service.support.InviteCodeSupport;
import com.nemonicworld.invite.service.support.InviteJoinEventLogger;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InviteJoinUseCase {

    private static final String INVITE_NOT_FOUND_MESSAGE = "초대코드를 찾을 수 없습니다.";
    private static final String UNSUPPORTED_BOOTH_TYPE_MESSAGE = "지원하지 않는 부스 타입입니다.";

    private final InviteRepository inviteRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final List<InviteJoinHandler> inviteJoinHandlers;
    private final InviteCodeSupport inviteCodeSupport;
    private final InviteJoinEventLogger inviteJoinEventLogger;

    @Transactional(readOnly = true)
    public InviteJoinResponse joinByInviteCode(String inviteCode, String userUuidValue) {
        long startedAt = inviteJoinEventLogger.startTimer();
        InviteMetadata invite = null;
        String boothType = null;
        AppUser user = null;
        try {
            inviteCodeSupport.validateInviteCode(inviteCode);
            user = anonymousUserResolver.resolve(userUuidValue);
            invite = findInvite(inviteCode);
            inviteCodeSupport.validateNotExpired(invite);
            boothType = inviteCodeSupport.normalizeBoothType(invite.boothType());
            InviteJoinHandler handler = findInviteJoinHandler(boothType);
            inviteJoinEventLogger.logRequested(inviteCode, user, invite, boothType);

            InviteJoinResponse response = handler.join(invite, user);
            inviteJoinEventLogger.logSucceeded(inviteCode, user, response, startedAt);

            return response;
        } catch (RuntimeException e) {
            String blockedBoothType = boothType == null && invite != null
                ? inviteCodeSupport.normalizeBoothType(invite.boothType())
                : boothType;
            inviteJoinEventLogger.logBlocked(inviteCode, userUuidValue, invite, blockedBoothType, e, startedAt);
            throw e;
        }
    }

    private InviteMetadata findInvite(String inviteCode) {
        return inviteRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new NotFoundException(INVITE_NOT_FOUND_MESSAGE));
    }

    private InviteJoinHandler findInviteJoinHandler(String boothType) {
        for (InviteJoinHandler handler : inviteJoinHandlers) {
            if (handler.supports(boothType)) {
                return handler;
            }
        }

        throw new BadRequestException(UNSUPPORTED_BOOTH_TYPE_MESSAGE);
    }
}
