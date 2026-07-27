package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UpdateDownloadQueueTest {

    @Test
    public void advancesAfterEachFailedAttemptAndStopsAtEnd() {
        UpdateDownloadQueue queue = new UpdateDownloadQueue(List.of("first", "second", "direct"));

        assertEquals(3, queue.size());
        assertEquals("first", queue.next());
        assertEquals("second", queue.next());
        assertEquals("direct", queue.next());
        assertNull(queue.next());
    }

    @Test
    public void cancelPreventsFurtherRetries() {
        UpdateDownloadQueue queue = new UpdateDownloadQueue(List.of("first", "second"));

        assertEquals("first", queue.next());
        queue.cancel();
        assertNull(queue.next());
    }
}
