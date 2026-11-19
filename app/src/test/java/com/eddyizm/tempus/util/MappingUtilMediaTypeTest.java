package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MappingUtilMediaTypeTest {

    @Test
    public void mediaTypeOf_keepsPodcastsAndMapsEverythingElseToMusic() {
        assertEquals("podcast", MappingUtil.mediaTypeOf("podcast"));
        assertEquals("podcast", MappingUtil.mediaTypeOf("podcastepisode"));
        assertEquals("music", MappingUtil.mediaTypeOf("music"));
        assertEquals("music", MappingUtil.mediaTypeOf("audiobook"));
        assertEquals("music", MappingUtil.mediaTypeOf("video"));
        assertEquals("music", MappingUtil.mediaTypeOf(null));
    }
}
