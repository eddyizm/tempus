package com.eddyizm.tempus.util;

import com.eddyizm.tempus.subsonic.models.MusicFolder;

import java.util.List;

public class MusicFolderUtil {
    private MusicFolderUtil() {
    }

    /**
     * The name to show for the library currently in force, or null when nothing is filtered and the
     * caller should name every library instead.
     *
     * A stored id with no match in the list is returned as itself. The request layer is still
     * sending that id, so naming it is true, where saying every library would not be.
     */
    public static String resolveMusicFolderName(List<MusicFolder> musicFolders, String activeMusicFolderId) {
        if (activeMusicFolderId == null) return null;
        if (musicFolders == null) return activeMusicFolderId;

        for (MusicFolder musicFolder : musicFolders) {
            if (activeMusicFolderId.equals(musicFolder.getId())) {
                return musicFolder.getName() != null ? musicFolder.getName() : musicFolder.getId();
            }
        }

        return activeMusicFolderId;
    }
}
