package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.Child;

import org.junit.Test;

public class ExternalAudioWriterTest {

    @Test
    public void oggMimePreservesOggAndOpusSourceMetadataWhenUntranscoded() {
        for (String suffix : new String[]{"ogg", "opus"}) {
            Child child = childWithAudioMetadata(suffix);

            assertEquals(suffix, ExternalAudioWriter.resolveDownloadExtension("oga", child, downloadUri()));

            Download download = ExternalAudioWriter.createDownloadMetadata(
                    child, "audio/ogg", "oga", downloadUri());
            assertEquals(suffix, download.getSuffix());
            assertEquals(Integer.valueOf(44100), download.getSamplingRate());
            assertEquals(Integer.valueOf(16), download.getBitDepth());
        }
    }

    @Test
    public void explicitTranscodeFormatOverridesAmbiguousOggMime() {
        Child child = childWithAudioMetadata("ogg");
        child.setBitrate(3216);
        String uri = "https://subsonic.example.com/rest/stream?format=opus&maxBitRate=0&id=song-1";

        assertEquals("opus", ExternalAudioWriter.resolveDownloadExtension("oga", child, uri));

        Download download = ExternalAudioWriter.createDownloadMetadata(child, "audio/ogg", "oga", uri);
        assertEquals("opus", download.getSuffix());
        assertEquals("audio/opus", download.getContentType());
        assertNull(download.getBitrate());
        assertNull(download.getSamplingRate());
        assertNull(download.getBitDepth());
    }

    private static Child childWithAudioMetadata(String suffix) {
        Child child = new Child("song-1");
        child.setSuffix(suffix);
        child.setSamplingRate(44100);
        child.setBitDepth(16);
        return child;
    }

    private static String downloadUri() {
        return "https://subsonic.example.com/rest/download?id=song-1";
    }
}
