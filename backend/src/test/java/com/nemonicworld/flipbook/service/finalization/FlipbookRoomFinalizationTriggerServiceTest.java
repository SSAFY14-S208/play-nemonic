package com.nemonicworld.flipbook.service.finalization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomFinalizationTriggerServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";

    @Mock
    private FlipbookRoomFinalizationService flipbookRoomFinalizationService;

    @Test
    void triggerFinalizationAsyncDelegatesToExecutor() {
        FlipbookRoomFinalizationTriggerService service = new FlipbookRoomFinalizationTriggerService(
            flipbookRoomFinalizationService, Runnable::run);

        service.triggerFinalizationAsync(ROOM_CODE);

        verify(flipbookRoomFinalizationService).triggerFinalization(ROOM_CODE);
    }

    @Test
    void triggerFinalizationAsyncDoesNotFailCallerWhenExecutorRejects() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        FlipbookRoomFinalizationTriggerService service = new FlipbookRoomFinalizationTriggerService(
            flipbookRoomFinalizationService, rejectingExecutor);

        assertThatCode(() -> service.triggerFinalizationAsync(ROOM_CODE)).doesNotThrowAnyException();

        verifyNoInteractions(flipbookRoomFinalizationService);
    }

    @Test
    void triggerFinalizationAsyncDoesNotFailCallerWhenFinalizationFails() {
        willThrow(new IllegalStateException("boom")).given(flipbookRoomFinalizationService)
            .triggerFinalization(ROOM_CODE);
        FlipbookRoomFinalizationTriggerService service = new FlipbookRoomFinalizationTriggerService(
            flipbookRoomFinalizationService, Runnable::run);

        assertThatCode(() -> service.triggerFinalizationAsync(ROOM_CODE)).doesNotThrowAnyException();

        verify(flipbookRoomFinalizationService).triggerFinalization(ROOM_CODE);
    }
}
