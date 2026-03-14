package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.util.DisplayMetrics;

public class TileSizeManager {

    private static TileSizeManager instance;

    private int tileSizePx;
    private int tileSpanCount;
    private int tileSpacing;
    private int genreSizePx;
    private int genreSpanCount;
    private int genreSpacing;
    private int GenreSpacing;
    private int discoverWidthPx;
    private int discoverHeightPx;
    private boolean tileIsInitialized;
    private boolean genreIsInitialized;
    private boolean discoverIsInitialized;

    private TileSizeManager() {
    }

    public static TileSizeManager getInstance() {
        if (instance == null) {
            instance = new TileSizeManager();
        }
        return instance;
    }

    public int getTileSizePx(Context context) {
        if( !tileIsInitialized )
            calculateTileSize(context);
        return tileSizePx;
    }
    public int getTileSpanCount(Context context) {
        if( !tileIsInitialized )
            calculateTileSize(context);
        return tileSpanCount;
    }
    public int getTileSpacing(Context context) {
        if( !tileIsInitialized )
            calculateTileSize(context);
        return tileSpacing;
    }
    public int getGenreSizePx(Context context) {
        if( !genreIsInitialized )
            calculateGenreSize(context);
        return genreSizePx;
    }
    public int getGenreSpanCount(Context context) {
        if( !genreIsInitialized )
            calculateGenreSize(context);
        return genreSpanCount;
    }
    public int getGenreSpacing(Context context) {
        if( !genreIsInitialized )
            calculateGenreSize(context);
        return genreSpacing;
    }
    public int getDiscoverWidthPx(Context context) {
        if( !discoverIsInitialized )
            calculateTileSize(context);
        return discoverWidthPx;
    }
    public int getDiscoverHeightPx(Context context) {
        if( !discoverIsInitialized )
            calculateTileSize(context);
        return discoverHeightPx;
    }

    public void calculateTileSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float screenWidth = metrics.widthPixels;
        float screenHeight = metrics.heightPixels;

        // retrieve the divisor in the preferences
        int userTileSize = Math.max(2, Math.min(6, Preferences.getTileSize()));
        float divisor = (float)userTileSize;

        // little pading = 10
        tileSizePx = Math.round(Math.min(screenWidth, screenHeight) / divisor) - 10;
        tileSpanCount = Math.max(2, Math.round(screenWidth / (float)tileSizePx) );

        switch (userTileSize) {
            default:
            case 2: // XL
                tileSpacing = 20;
                break;
            case 3: // L
                tileSpacing = 15;
                break;
            case 4: // M
                tileSpacing = 10;
                break;
            case 5: // S
                tileSpacing = 6;
                break;
            case 6: // SX
                tileSpacing = 2;
                break;
        }
        tileIsInitialized = true;
    }

    public void calculateGenreSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float screenWidth = metrics.widthPixels;
        float screenHeight = metrics.heightPixels;

        // retrieve the divisor in the preferences
        int userTileSize = Math.max(2, Math.min(3, Preferences.getTileSize()));
        float divisor = (float)userTileSize;

        // little pading = 10
        genreSizePx = Math.round(Math.min(screenWidth, screenHeight) / divisor) - 10;
        genreSpanCount = Math.max(2, Math.round(screenWidth / (float)genreSizePx) );

        switch (userTileSize) {
            default:
            case 2: // XL
                genreSpacing = 20;
                break;
            case 3: // L
                genreSpacing = 15;
                break;
            case 4: // M
                genreSpacing = 10;
                break;
            case 5: // S
                genreSpacing = 6;
                break;
            case 6: // XS
                genreSpacing = 2;
                break;
        }
        genreIsInitialized = true;
    }

    public void calculateDiscoverSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float screenWidth = metrics.widthPixels;
        float screenHeight = metrics.heightPixels;
        float discoverDivisor;

        // retrieve the divisor in the preferences
        int userTileSize = Math.max(2, Math.min(6, Preferences.getTileSize()));

        switch (userTileSize) {
            default:
            case 2: // XL
                discoverDivisor = 1.0f;
                break;
            case 3: // L
                discoverDivisor = 1.25f;
                break;
            case 4: // M
                discoverDivisor = 1.5f;
                break;
            case 5: // S
                discoverDivisor = 1.75f;
                break;
            case 6: // XS
                discoverDivisor = 2.0f;
                break;
        }

        discoverWidthPx = Math.round(Math.min(screenWidth, screenHeight) / discoverDivisor) - 50;
        discoverHeightPx = Math.round((float)discoverWidthPx * 0.6f);
        discoverIsInitialized = true;
    }
}
