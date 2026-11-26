package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.Child;

import org.mockito.MockedStatic;
import org.junit.Test;

public class ExternalAudioWriterTest {

    @Test
    public void oggMimePreservesOggAndOpusSourceMetadataWhenUntranscoded() {
        for (String suffix : new String[]{"ogg", "opus"}) {
            Child child = childWithAudioMetadata(suffix);

            assertEquals(suffix, ExternalAudioWriter.resolveDownloadExtension("oga", child, downloadUri()));

            Download download = ExternalAudioWriter.createDownloadMetadata(
                    child, "audio/ogg", "oga", downloadUri(), 7_900_000L);
            assertEquals(suffix, download.getSuffix());
            assertEquals(Long.valueOf(7_900_000L), download.getSize());
            assertEquals(Integer.valueOf(44100), download.getSamplingRate());
            assertEquals(Integer.valueOf(16), download.getBitDepth());
        }
    }

    @Test
    public void explicitTranscodeFormatOverridesAmbiguousOggMime() {
        Child child = childWithAudioMetadata("ogg");
        child.setBitrate(3216);
        child.setSize(186_000_000L);
        String uri = "https://subsonic.example.com/rest/stream?format=opus&maxBitRate=0&id=song-1";

        assertEquals("opus", ExternalAudioWriter.resolveDownloadExtension("oga", child, uri));

        Download download = ExternalAudioWriter.createDownloadMetadata(
                child, "audio/ogg", "oga", uri, 7_900_000L);
        assertEquals("opus", download.getSuffix());
        assertEquals("audio/opus", download.getContentType());
        assertEquals(Long.valueOf(7_900_000L), download.getSize());
        assertNull(download.getBitrate());
        assertNull(download.getSamplingRate());
        assertNull(download.getBitDepth());
    }

    @Test
    public void recordsAutoDownloadSizeUsingNormalizedAudioReaderKey() {
        Child child = new Child("song-1");
        child.setArtist("Beyoncé");
        child.setTitle("Déjà Vu");
        child.setAlbum("Rénaissance");

        try (MockedStatic<ExternalDownloadMetadataStore> metadata =
                     mockStatic(ExternalDownloadMetadataStore.class)) {
            ExternalAudioReader.recordDownloadedFileSize(child, 7_900_000L);

            metadata.verify(() -> ExternalDownloadMetadataStore.recordSize(
                    "beyonce - deja vu (renaissance)", 7_900_000L));
        }
    }

    @Test
    public void pendingPathBRequestsReserveMediaIdOnlyOnce() {
        String mediaId = "pending-song";
        assertTrue(ExternalAudioWriter.tryMarkPending(mediaId));
        try {
            assertFalse(ExternalAudioWriter.tryMarkPending(mediaId));
            assertTrue(ExternalAudioWriter.isPending(mediaId));
        } finally {
            ExternalAudioWriter.clearPending(mediaId);
        }
        assertFalse(ExternalAudioWriter.isPending(mediaId));
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
