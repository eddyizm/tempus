package com.eddyizm.tempus.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.subsonic.models.Child;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class SongListPageViewModelTest {

    @Test
    public void uniqueSongs_keepsTheFirstOccurrenceAndOrder() {
        Child first = new Child("a");
        Child duplicate = new Child("a");
        Child second = new Child("b");

        List<Child> unique = SongListPageViewModel.uniqueSongs(
                Arrays.asList(first, duplicate, second));

        assertEquals(Arrays.asList(first, second), unique);
    }

    @Test
    public void uniqueSongs_handlesNullAndEmptyLists() {
        assertTrue(SongListPageViewModel.uniqueSongs(null).isEmpty());
        assertTrue(SongListPageViewModel.uniqueSongs(Collections.emptyList()).isEmpty());
    }
}
