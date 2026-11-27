package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.Child;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class ArtistRepositoryTest {

    @Test
    public void chooseArtistSongs_keepsStrictMatchesWhenFallbackIsOff() {
        List<Child> matched = Arrays.asList(new Child("a"));
        List<Child> all = Arrays.asList(new Child("a"), new Child("b"));

        assertSame(matched, ArtistRepository.chooseArtistSongs(matched, all, false));
    }

    @Test
    public void chooseArtistSongs_usesAllAlbumTracksWhenNoOwnTracksExist() {
        List<Child> all = Arrays.asList(new Child("a"), new Child("b"));

        assertSame(all, ArtistRepository.chooseArtistSongs(Collections.emptyList(), all, true));
    }

    @Test
    public void chooseArtistSongs_keepsStrictMatchesEvenWhenFallbackIsOn() {
        List<Child> matched = Arrays.asList(new Child("a"));
        List<Child> all = Arrays.asList(new Child("a"), new Child("b"));

        assertSame(matched, ArtistRepository.chooseArtistSongs(matched, all, true));
    }

    @Test
    public void chooseArtistSongs_handlesNullLists() {
        assertTrue(ArtistRepository.chooseArtistSongs(null, null, true).isEmpty());
        assertTrue(ArtistRepository.chooseArtistSongs(null, null, false).isEmpty());
    }

    @Test
    public void isCreditedToArtist_matchesAlbumsByIdOrName() {
        AlbumID3 byId = new AlbumID3();
        byId.setArtistId("artist-1");
        byId.setArtist("Someone Else");
        assertTrue(ArtistRepository.isCreditedToArtist(byId, "artist-1", "Chillhop Music"));

        AlbumID3 byName = new AlbumID3();
        byName.setArtist("chillhop music");
        assertTrue(ArtistRepository.isCreditedToArtist(byName, null, "Chillhop Music"));

        AlbumID3 unrelated = new AlbumID3();
        unrelated.setArtistId("other");
        unrelated.setArtist("Someone Else");
        assertFalse(ArtistRepository.isCreditedToArtist(unrelated, "artist-1", "Chillhop Music"));
    }
}
