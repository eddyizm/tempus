package com.cappielloantonio.tempo.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void isTranscodedFormat_sameCodecIsNotTranscoded() {
        assertFalse(MusicUtil.isTranscodedFormat("flac", "flac"));
        assertFalse(MusicUtil.isTranscodedFormat("FLAC", "flac"));
    }

    @Test
    public void isTranscodedFormat_containerCodecIsNotTranscoded() {
        assertFalse(MusicUtil.isTranscodedFormat("aac", "m4a"));
        assertFalse(MusicUtil.isTranscodedFormat("alac", "m4a"));
        assertFalse(MusicUtil.isTranscodedFormat("vorbis", "ogg"));
        assertFalse(MusicUtil.isTranscodedFormat("opus", "oga"));
    }

    @Test
    public void isTranscodedFormat_differentCodecIsTranscoded() {
        assertTrue(MusicUtil.isTranscodedFormat("mp3", "flac"));
        assertTrue(MusicUtil.isTranscodedFormat("mp3", "m4a"));
        assertTrue(MusicUtil.isTranscodedFormat("opus", "flac"));
    }

    @Test
    public void isTranscodedFormat_missingSuffixIsNotTranscoded() {
        assertFalse(MusicUtil.isTranscodedFormat("flac", null));
        assertFalse(MusicUtil.isTranscodedFormat("flac", ""));
    }
}
