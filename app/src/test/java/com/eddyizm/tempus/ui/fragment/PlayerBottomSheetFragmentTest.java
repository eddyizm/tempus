package com.eddyizm.tempus.ui.fragment;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaBrowser;

import org.junit.Test;

public class PlayerBottomSheetFragmentTest {
    @Test
    public void positionDiscontinuitiesUpdateDurationWithoutReloadingMetadata() {
        MediaBrowser mediaBrowser = mock(MediaBrowser.class);
        Player.Listener listener = new PlayerBottomSheetFragment().createMediaControllerListener(mediaBrowser);
        Player.PositionInfo earlier = new Player.PositionInfo(null, 0, null, null, 0, 1_000L, 1_000L, -1, -1);
        Player.PositionInfo later = new Player.PositionInfo(null, 0, null, null, 0, 30_000L, 30_000L, -1, -1);

        for (int reason : new int[]{Player.DISCONTINUITY_REASON_SEEK,
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                Player.DISCONTINUITY_REASON_REMOVE}) {
            clearInvocations(mediaBrowser);
            listener.onPositionDiscontinuity(earlier, later, reason);
            listener.onPositionDiscontinuity(later, earlier, reason);

            verify(mediaBrowser, times(2)).getContentDuration();
            verify(mediaBrowser, never()).getMediaMetadata();
        }
    }

    @Test
    public void mediaItemTransitionsStillRefreshMetadataAndDuration() {
        MediaBrowser mediaBrowser = mock(MediaBrowser.class);
        Player.Listener listener = new PlayerBottomSheetFragment().createMediaControllerListener(mediaBrowser);

        for (int reason : new int[]{Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT}) {
            clearInvocations(mediaBrowser);
            listener.onMediaItemTransition(null, reason);

            verify(mediaBrowser).getMediaMetadata();
            verify(mediaBrowser).getContentDuration();
        }
    }

    @Test
    public void metadataChangesStillUpdateDuration() {
        MediaBrowser mediaBrowser = mock(MediaBrowser.class);
        Player.Listener listener = new PlayerBottomSheetFragment().createMediaControllerListener(mediaBrowser);

        listener.onMediaMetadataChanged(MediaMetadata.EMPTY);

        verify(mediaBrowser).getContentDuration();
    }
}
