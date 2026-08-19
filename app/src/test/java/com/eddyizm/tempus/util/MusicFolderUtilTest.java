package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.eddyizm.tempus.subsonic.models.MusicFolder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class MusicFolderUtilTest {

    private static final List<MusicFolder> TWO_LIBRARIES = Arrays.asList(
            new MusicFolder("1", "Music Library"),
            new MusicFolder("2", "Test Library")
    );

    @Test
    public void noSelectionMeansEveryLibrary() {
        assertNull(MusicFolderUtil.resolveMusicFolderName(TWO_LIBRARIES, null));
    }

    @Test
    public void selectedLibraryIsNamed() {
        assertEquals("Test Library", MusicFolderUtil.resolveMusicFolderName(TWO_LIBRARIES, "2"));
    }

    @Test
    public void libraryWithNoNameFallsBackToItsId() {
        List<MusicFolder> folders = Collections.singletonList(new MusicFolder("7", null));
        assertEquals("7", MusicFolderUtil.resolveMusicFolderName(folders, "7"));
    }

    @Test
    public void staleSelectionIsNamedNotReportedAsEveryLibrary() {
        assertEquals("99", MusicFolderUtil.resolveMusicFolderName(TWO_LIBRARIES, "99"));
    }

    @Test
    public void selectionSurvivesAnAbsentFolderList() {
        assertEquals("2", MusicFolderUtil.resolveMusicFolderName(null, "2"));
    }

    @Test
    public void noSelectionAndNoFolderListMeansEveryLibrary() {
        assertNull(MusicFolderUtil.resolveMusicFolderName(null, null));
    }
}
