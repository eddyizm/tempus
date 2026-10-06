package com.eddyizm.tempus.ui.adapter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.eddyizm.tempus.util.Constants;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PodcastEpisodeAdapterTest {

    private ClickCallback clickCallback;
    private PodcastEpisodeAdapter adapter;

    @Before
    public void setUp() {
        clickCallback = mock(ClickCallback.class);
        PodcastEpisodeAdapter realAdapter = new PodcastEpisodeAdapter(clickCallback);
        adapter = spy(realAdapter);
        doNothing().when(adapter).notifyDataSetChanged();
    }

    @Test
    public void testInitialState_getItemCountReturnsZero() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSortByAll_withoutSetItems_doesNotCrash() {
        adapter.sort(Constants.PODCAST_FILTER_BY_ALL);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSortByDownload_withoutSetItems_doesNotCrash() {
        adapter.sort(Constants.PODCAST_FILTER_BY_DOWNLOAD);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSetItemsNull_doesNotCrash() {
        adapter.setItems(null);
        assertEquals(0, adapter.getItemCount());

        adapter.sort(Constants.PODCAST_FILTER_BY_ALL);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSetItemsAndFilter() {
        List<PodcastEpisode> episodes = new ArrayList<>();

        PodcastEpisode ep1 = new PodcastEpisode();
        ep1.setId("1");
        ep1.setStatus("completed");

        PodcastEpisode ep2 = new PodcastEpisode();
        ep2.setId("2");
        ep2.setStatus("skipped");

        episodes.add(ep1);
        episodes.add(ep2);
        episodes.add(null);

        adapter.setItems(episodes);

        // Default setItems filters by status "completed"
        assertEquals(1, adapter.getItemCount());

        // Sort BY ALL shows all 2 episodes
        adapter.sort(Constants.PODCAST_FILTER_BY_ALL);
        assertEquals(2, adapter.getItemCount());

        // Sort BY DOWNLOAD shows 1 completed episode
        adapter.sort(Constants.PODCAST_FILTER_BY_DOWNLOAD);
        assertEquals(1, adapter.getItemCount());
    }
}
