package com.cappielloantonio.tempo.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MusicUtilTest {

    @Test
    public void audioFormatLabel_mapsKnownMimeTypes() {
        assertEquals("flac", MusicUtil.audioFormatLabel("audio/flac"));
        assertEquals("opus", MusicUtil.audioFormatLabel("audio/opus"));
        assertEquals("vorbis", MusicUtil.audioFormatLabel("audio/vorbis"));
        assertEquals("aac", MusicUtil.audioFormatLabel("audio/mp4a-latm"));
        assertEquals("aac", MusicUtil.audioFormatLabel("audio/aac"));
        assertEquals("mp3", MusicUtil.audioFormatLabel("audio/mpeg"));
        assertEquals("alac", MusicUtil.audioFormatLabel("audio/alac"));
        assertEquals("ogg", MusicUtil.audioFormatLabel("audio/ogg"));
    }

    // eac3 must be matched before ac3, otherwise "audio/eac3" would fall through to "ac3".
    @Test
    public void audioFormatLabel_disambiguatesEac3FromAc3() {
        assertEquals("eac3", MusicUtil.audioFormatLabel("audio/eac3"));
        assertEquals("ac3", MusicUtil.audioFormatLabel("audio/ac3"));
    }

    @Test
    public void audioFormatLabel_treatsRawAndWavAsWav() {
        assertEquals("wav", MusicUtil.audioFormatLabel("audio/raw"));
        assertEquals("wav", MusicUtil.audioFormatLabel("audio/wav"));
    }

    @Test
    public void audioFormatLabel_isCaseInsensitive() {
        assertEquals("flac", MusicUtil.audioFormatLabel("AUDIO/FLAC"));
    }

    @Test
    public void audioFormatLabel_fallsBackToSubtypeForUnknownMime() {
        assertEquals("xyz", MusicUtil.audioFormatLabel("audio/xyz"));
    }

    @Test
    public void audioFormatLabel_returnsNullForNull() {
        assertNull(MusicUtil.audioFormatLabel(null));
    }
}
