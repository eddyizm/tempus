package com.cappielloantonio.tempo.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;

import org.junit.Test;

public class MetadataDurationMediaSourceTest {
    private static final long ONE_MINUTE_US = 60_000_000L;

    @Test
    public void fillsWhenARealTimelineReportsNoDuration() {
        // The case this exists for: a transcode the server performed, sent with no Content-Length.
        assertTrue(MetadataDurationMediaSource.shouldFill(C.TIME_UNSET, false, ONE_MINUTE_US));
    }

    @Test
    public void ignoresThePlaceholderTimeline() {
        // Every progressive source publishes this first, for every item on every server. Filling it
        // would briefly apply the metadata duration to direct play, local files and downloads too.
        assertFalse(MetadataDurationMediaSource.shouldFill(C.TIME_UNSET, true, ONE_MINUTE_US));
    }

    @Test
    public void leavesADurationTheStreamKnowsAlone() {
        assertFalse(MetadataDurationMediaSource.shouldFill(ONE_MINUTE_US, false, ONE_MINUTE_US));
    }

    @Test
    public void doesNothingWithoutAMetadataDuration() {
        // Internet radio has no duration in its metadata, and must stay endless.
        assertFalse(MetadataDurationMediaSource.shouldFill(C.TIME_UNSET, false, C.TIME_UNSET));
    }

    @Test
    public void doesNothingWhenNeitherSideKnows() {
        assertFalse(MetadataDurationMediaSource.shouldFill(C.TIME_UNSET, true, C.TIME_UNSET));
    }
}
