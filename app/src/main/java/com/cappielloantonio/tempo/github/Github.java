package com.cappielloantonio.tempo.github;

import com.cappielloantonio.tempo.github.api.release.ReleaseClient;

public class Github {
    private static final String OWNER = "eddyizm";
    private static final String REPO = "Tempus";
    private ReleaseClient releaseClient;

    public ReleaseClient getReleaseClient() {
        if (releaseClient == null) {
            releaseClient = new ReleaseClient(this);
        }

        return releaseClient;
    }

    public String getUrl() {
        return "https://api.github.com/";
    }

    public static String getOwner() {
        return OWNER;
    }

    public static String getRepo() {
        return REPO;
    }
}
