package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReaderPlaybackRoutingSourceTest {

    @Test
    public void readerResultNeverFallsThroughToVideoPipeline() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java");

        int route = source.indexOf("NovelRouter.isReaderUrl(result)");
        int pipeline = source.indexOf("player().parse(", route);
        int unconditionalReturn = source.indexOf("return;", route);

        assertTrue("reader routing must exist in startPlayer", route >= 0);
        assertTrue("reader result must return before the video pipeline", unconditionalReturn > route
                && (pipeline < 0 || unconditionalReturn < pipeline));
    }

    @Test
    public void readerDispatchKeepsHostPageInBackstack() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);
            int dispatch = source.indexOf("ContentDispatcher.dispatchResult(this");
            int stop = source.indexOf("stopPlayback();", dispatch);
            int finish = source.indexOf("finish();", stop);
            int blockEnd = source.indexOf('}', stop);

            assertTrue(path + " must dispatch results through ContentDispatcher", dispatch >= 0);
            assertTrue(path + " must stop playback after reader dispatch", stop > dispatch);
            assertTrue(path + " must keep its page in the back stack after reader dispatch",
                    finish < 0 || finish > blockEnd);
        }
    }

    @Test
    public void reclaimedOrRepeatedPlayerResultsAreNotStartedTwice() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);

            assertTrue(path + " must track the result already applied to the player",
                    source.contains("mAppliedPlayerResult"));
            assertTrue(path + " must ignore a duplicate player result while playback remains active",
                    source.contains("if (result == mAppliedPlayerResult && !player().isEmpty()) return;"));
        }
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
