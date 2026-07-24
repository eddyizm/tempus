package com.cappielloantonio.tempo.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.documentfile.provider.DocumentFile;

import org.junit.Test;

public class ExternalAudioWriterFilenameTest {

    @Test
    public void sanitizeFileName_replacesIllegalCharactersAndPreservesLegalOnes() {
        String result = ExternalAudioWriter.sanitizeFileName("A/B:C*?\"<>|");
        // Illegal characters are replaced with underscores, legal ones kept.
        assertFalse(result.contains("/"));
        assertFalse(result.contains(":"));
        assertFalse(result.contains("*"));
        assertFalse(result.contains("?"));
        assertFalse(result.contains("\""));
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains("|"));
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
        assertTrue(result.contains("C"));

        assertEquals("Simple Name", ExternalAudioWriter.sanitizeFileName("  Simple   Name  "));
        assertEquals("Artist - Title (Album)", ExternalAudioWriter.sanitizeFileName("Artist - Title (Album)"));
    }

    @Test
    public void normalizeForComparison_stripsDiacriticsAndLowercases() {
        assertEquals("cafe.mp3", ExternalAudioWriter.normalizeForComparison("Café.MP3"));
        assertEquals("artiste - titre", ExternalAudioWriter.normalizeForComparison("Artiste - Titre"));
        // Accented characters must collapse to their ASCII base for matching.
        assertEquals("queen", ExternalAudioWriter.normalizeForComparison("Quéeñ"));
    }

    @Test
    public void findFile_matchesExistingFileCaseAndDiacriticInsensitive() {
        DocumentFile dir = mock(DocumentFile.class);
        DocumentFile existing = mock(DocumentFile.class);
        when(existing.isDirectory()).thenReturn(false);
        when(existing.getName()).thenReturn("Björk - Hyperballad (Album).mp3");
        when(dir.listFiles()).thenReturn(new DocumentFile[]{existing});

        DocumentFile found = ExternalAudioWriter.findFile(dir, "bjork - hyperballad (album).mp3");
        assertSame(existing, found);
    }

    @Test
    public void findFile_skipsDirectories() {
        DocumentFile dir = mock(DocumentFile.class);
        DocumentFile subDir = mock(DocumentFile.class);
        when(subDir.isDirectory()).thenReturn(true);
        when(subDir.getName()).thenReturn("bjork - hyperballad (album).mp3");
        when(dir.listFiles()).thenReturn(new DocumentFile[]{subDir});

        // A directory with a matching name must not be mistaken for the file.
        assertNull(ExternalAudioWriter.findFile(dir, "bjork - hyperballad (album).mp3"));
    }

    @Test
    public void findFile_returnsNullWhenNoMatch() {
        DocumentFile dir = mock(DocumentFile.class);
        DocumentFile other = mock(DocumentFile.class);
        when(other.isDirectory()).thenReturn(false);
        when(other.getName()).thenReturn("Different Track.mp3");
        when(dir.listFiles()).thenReturn(new DocumentFile[]{other});

        assertNull(ExternalAudioWriter.findFile(dir, "bjork - hyperballad (album).mp3"));
    }
}
