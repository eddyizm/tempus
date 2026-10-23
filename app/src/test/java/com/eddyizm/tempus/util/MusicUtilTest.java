package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.Bundle;

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

    @Test
    public void transportOf_readsWifiAndCellular() {
        NetworkCapabilities wifi = mock(NetworkCapabilities.class);
        when(wifi.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        assertEquals(NetworkCapabilities.TRANSPORT_WIFI, MusicUtil.transportOf(wifi));

        NetworkCapabilities cellular = mock(NetworkCapabilities.class);
        when(cellular.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);
        assertEquals(NetworkCapabilities.TRANSPORT_CELLULAR, MusicUtil.transportOf(cellular));
    }

    // Issue 198: a VPN reports neither transport while the default network hands over.
    @Test
    public void transportOf_doesNotTreatAnUnknownTransportAsWifi() {
        NetworkCapabilities neither = mock(NetworkCapabilities.class);
        int transport = MusicUtil.transportOf(neither);
        assertNotEquals(NetworkCapabilities.TRANSPORT_WIFI, transport);
        assertNotEquals(NetworkCapabilities.TRANSPORT_CELLULAR, transport);
    }

    @Test
    public void transportOf_treatsNoCapabilitiesAsNoTransport() {
        int transport = MusicUtil.transportOf(null);
        assertNotEquals(NetworkCapabilities.TRANSPORT_WIFI, transport);
        assertNotEquals(NetworkCapabilities.TRANSPORT_CELLULAR, transport);
    }

    private static Bundle extrasWith(String path, String suffix) {
        Bundle extras = mock(Bundle.class);
        when(extras.getString("path")).thenReturn(path);
        when(extras.getString("suffix")).thenReturn(suffix);
        return extras;
    }

    // A transcoded download's suffix is rewritten to the download's format and its path is not.
    @Test
    public void sourceSuffix_readsTheExtensionOffThePath() {
        assertEquals("flac", MusicUtil.sourceSuffix(extrasWith("Artist/Album/05 track.flac", "opus")));
    }

    // A dot in a folder name is not an extension, and neither is a trailing one.
    @Test
    public void sourceSuffix_fallsBackToTheSuffixWhenThePathHasNoExtension() {
        assertEquals("opus", MusicUtil.sourceSuffix(extrasWith("Artist/Album.Deluxe/05 track", "opus")));
        assertEquals("opus", MusicUtil.sourceSuffix(extrasWith("D:\\Music\\Album.Deluxe\\05 track", "opus")));
        assertEquals("opus", MusicUtil.sourceSuffix(extrasWith("Artist/Album/05 track.", "opus")));
        assertEquals("opus", MusicUtil.sourceSuffix(extrasWith("track", "opus")));
        assertEquals("opus", MusicUtil.sourceSuffix(extrasWith(null, "opus")));
    }

    @Test
    public void sourceSuffix_isNullWhenNothingIsKnown() {
        assertNull(MusicUtil.sourceSuffix(null));
        assertNull(MusicUtil.sourceSuffix(extrasWith(null, null)));
    }

    @Test
    public void applyTranscodeMetadataFromUri_extractsFormatAndBitrate() {
        com.eddyizm.tempus.model.Download download = new com.eddyizm.tempus.model.Download(new com.eddyizm.tempus.subsonic.models.Child("song-1"));
        download.setSuffix("flac");
        download.setContentType("audio/flac");
        download.setSamplingRate(44100);
        download.setBitDepth(16);

        String uri = "https://subsonic.example.com/rest/stream?u=user&p=enc&v=1.16.1&c=tempus&format=opus&maxBitRate=128&id=song-1";
        MusicUtil.applyTranscodeMetadataFromUri(download, uri);

        assertEquals("opus", download.getSuffix());
        assertEquals("audio/opus", download.getContentType());
        assertEquals(Integer.valueOf(128), download.getBitrate());
        assertNull(download.getSamplingRate());
        assertNull(download.getBitDepth());
    }

    @Test
    public void applyTranscodeMetadataFromUri_noFormatParamIsNoOp() {
        com.eddyizm.tempus.model.Download download = new com.eddyizm.tempus.model.Download(new com.eddyizm.tempus.subsonic.models.Child("song-1"));
        download.setSuffix("flac");
        download.setContentType("audio/flac");
        download.setSamplingRate(44100);
        download.setBitDepth(16);

        String uri = "https://subsonic.example.com/rest/download?u=user&p=enc&v=1.16.1&c=tempus&id=song-1";
        MusicUtil.applyTranscodeMetadataFromUri(download, uri);

        assertEquals("flac", download.getSuffix());
        assertEquals("audio/flac", download.getContentType());
        assertEquals(Integer.valueOf(44100), download.getSamplingRate());
        assertEquals(Integer.valueOf(16), download.getBitDepth());
    }

    @Test
    public void applyTranscodeMetadataFromUri_rawFormatIsNoOp() {
        com.eddyizm.tempus.model.Download download = new com.eddyizm.tempus.model.Download(new com.eddyizm.tempus.subsonic.models.Child("song-1"));
        download.setSuffix("flac");
        download.setContentType("audio/flac");

        String uri = "https://subsonic.example.com/rest/stream?u=user&p=enc&v=1.16.1&c=tempus&format=raw&maxBitRate=0&id=song-1";
        MusicUtil.applyTranscodeMetadataFromUri(download, uri);

        assertEquals("flac", download.getSuffix());
        assertEquals("audio/flac", download.getContentType());
    }

    @Test
    public void applyTranscodeMetadataFromUri_opaqueUriOrNullIsSafe() {
        com.eddyizm.tempus.model.Download download = new com.eddyizm.tempus.model.Download(new com.eddyizm.tempus.subsonic.models.Child("song-1"));
        download.setSuffix("flac");

        MusicUtil.applyTranscodeMetadataFromUri(download, null);
        assertEquals("flac", download.getSuffix());

        MusicUtil.applyTranscodeMetadataFromUri(download, "mailto:nobody@example.com");
        assertEquals("flac", download.getSuffix());

        MusicUtil.applyTranscodeMetadataFromUri(null, "https://subsonic.example.com/rest/stream?format=opus");
    }
}
