package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationAsyncTrigger;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class RelayRoomFinalizationAsyncTriggerTest {

    private static final String ROOM_CODE = "AB3K9Q";

    @Mock
    private RelayRoomFinalizationService relayRoomFinalizationService;

    @Test
    void triggerRunsFinalizationOnTaskExecutor() {
        RelayRoomFinalizationAsyncTrigger trigger = new RelayRoomFinalizationAsyncTrigger(relayRoomFinalizationService,
            new SyncTaskExecutor());

        trigger.trigger(ROOM_CODE);

        verify(relayRoomFinalizationService).triggerFinalization(ROOM_CODE);
    }

    @Test
    void triggerDoesNotThrowWhenFinalizationFails() {
        RelayRoomFinalizationAsyncTrigger trigger = new RelayRoomFinalizationAsyncTrigger(relayRoomFinalizationService,
            new SyncTaskExecutor());
        willThrow(new InternalServerException("최종화 실패")).given(relayRoomFinalizationService)
            .triggerFinalization(ROOM_CODE);

        assertThatCode(() -> trigger.trigger(ROOM_CODE)).doesNotThrowAnyException();

        verify(relayRoomFinalizationService).triggerFinalization(ROOM_CODE);
    }

    @Test
    void triggerDoesNotThrowWhenTaskExecutorRejects() {
        TaskExecutor rejectingExecutor = task -> {
            throw new InternalServerException("작업 시작 실패");
        };
        RelayRoomFinalizationAsyncTrigger trigger = new RelayRoomFinalizationAsyncTrigger(relayRoomFinalizationService,
            rejectingExecutor);

        assertThatCode(() -> trigger.trigger(ROOM_CODE)).doesNotThrowAnyException();

        verifyNoInteractions(relayRoomFinalizationService);
    }
}
