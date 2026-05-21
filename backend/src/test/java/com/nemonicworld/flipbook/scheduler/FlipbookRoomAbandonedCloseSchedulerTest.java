package com.nemonicworld.flipbook.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.nemonicworld.flipbook.service.close.FlipbookRoomAbandonedCloseService;
import org.junit.jupiter.api.Test;

class FlipbookRoomAbandonedCloseSchedulerTest {

    @Test
    void closeAbandonedRoomsSkipsWhenDisabled() {
        FlipbookRoomAbandonedCloseService service = mock(FlipbookRoomAbandonedCloseService.class);
        FlipbookRoomAbandonedCloseScheduler scheduler = new FlipbookRoomAbandonedCloseScheduler(service, false);

        scheduler.closeAbandonedRooms();

        verify(service, never()).closeAbandonedRooms();
    }

    @Test
    void closeAbandonedRoomsDelegatesWhenEnabled() {
        FlipbookRoomAbandonedCloseService service = mock(FlipbookRoomAbandonedCloseService.class);
        FlipbookRoomAbandonedCloseScheduler scheduler = new FlipbookRoomAbandonedCloseScheduler(service, true);

        scheduler.closeAbandonedRooms();

        verify(service).closeAbandonedRooms();
    }
}
