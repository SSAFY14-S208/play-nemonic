package com.nemonicworld.infinitecanvas.service.support;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasInviteMetadataSyncService {

    public static final String BOOTH_TYPE_INFINITE_CANVAS = "infinite_canvas";

    private static final String DEFAULT_CANVAS_NAME_SUFFIX = "의 무한 캔버스";

    private final InviteRepository inviteRepository;

    public InfiniteCanvasInviteMetadataSyncService(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    public void syncWithCanvasState(InfiniteCanvasState state) {
        if (!StringUtils.hasText(state.inviteCode())) {
            return;
        }

        LocalDateTime expiresAt = resolveExpiresAt(state);
        Optional<InviteMetadata> inviteMetadata = inviteRepository.findByInviteCode(state.inviteCode())
            .map(invite -> invite.withExpiresAt(expiresAt)).or(() -> createRestoredInvite(state, expiresAt));

        inviteMetadata.ifPresent(invite -> inviteRepository.save(invite, InfiniteCanvasRepository.CANVAS_STATE_TTL));
    }

    private Optional<InviteMetadata> createRestoredInvite(InfiniteCanvasState state, LocalDateTime expiresAt) {
        return findOwnerNickname(state).map(ownerNickname -> new InviteMetadata(state.inviteCode(),
            BOOTH_TYPE_INFINITE_CANVAS, state.canvasId(), ownerNickname + DEFAULT_CANVAS_NAME_SUFFIX, expiresAt));
    }

    private Optional<String> findOwnerNickname(InfiniteCanvasState state) {
        return state.participants().stream().filter(participant -> participant.userUuid().equals(state.ownerUserUuid()))
            .map(InfiniteCanvasParticipant::nickname).findFirst();
    }

    private LocalDateTime resolveExpiresAt(InfiniteCanvasState state) {
        LocalDateTime baseTime = state.updatedAt() == null
            ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            : state.updatedAt();

        return baseTime.plus(InfiniteCanvasRepository.CANVAS_STATE_TTL);
    }
}
