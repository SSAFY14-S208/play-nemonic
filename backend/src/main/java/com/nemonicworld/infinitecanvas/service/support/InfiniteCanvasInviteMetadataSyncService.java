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
        if (!StringUtils.hasText(state.roomCode())) {
            return;
        }

        LocalDateTime expiresAt = resolveExpiresAt(state);
        Optional<InviteMetadata> inviteMetadata = createInviteMetadata(state, expiresAt);

        inviteMetadata.ifPresent(invite -> inviteRepository.save(invite, InfiniteCanvasRepository.CANVAS_STATE_TTL));
    }

    private Optional<InviteMetadata> createInviteMetadata(InfiniteCanvasState state, LocalDateTime expiresAt) {
        return findHostNickname(state).map(hostNickname -> new InviteMetadata(state.roomCode(),
            BOOTH_TYPE_INFINITE_CANVAS, state.roomCode(), hostNickname + DEFAULT_CANVAS_NAME_SUFFIX, expiresAt));
    }

    private Optional<String> findHostNickname(InfiniteCanvasState state) {
        return state.participants().stream().filter(participant -> participant.userUuid().equals(state.hostUserUuid()))
            .map(InfiniteCanvasParticipant::nickname).findFirst();
    }

    private LocalDateTime resolveExpiresAt(InfiniteCanvasState state) {
        LocalDateTime baseTime = state.updatedAt() == null
            ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            : state.updatedAt();

        return baseTime.plus(InfiniteCanvasRepository.CANVAS_STATE_TTL);
    }
}
