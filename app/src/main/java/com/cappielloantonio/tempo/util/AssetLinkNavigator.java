package com.cappielloantonio.tempo.util;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.subsonic.models.Genre;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.fragment.bottomsheetdialog.SongBottomSheetDialog;
import com.cappielloantonio.tempo.viewmodel.SongBottomSheetViewModel;

public final class AssetLinkNavigator {
    private final MainActivity activity;
    private final SongRepository songRepository = new SongRepository();
    private final AlbumRepository albumRepository = new AlbumRepository();
    private final ArtistRepository artistRepository = new ArtistRepository();
    private final PlaylistRepository playlistRepository = new PlaylistRepository();

    public AssetLinkNavigator(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    public void open(@Nullable AssetLinkUtil.AssetLink assetLink) {
        if (assetLink == null) {
            return;
        }
        switch (assetLink.type) {
            case AssetLinkUtil.TYPE_SONG:
                openSong(assetLink.id);
                break;
            case AssetLinkUtil.TYPE_ALBUM:
                openAlbum(assetLink.id);
                break;
            case AssetLinkUtil.TYPE_ARTIST:
                openArtist(assetLink.id);
                break;
            case AssetLinkUtil.TYPE_PLAYLIST:
                openPlaylist(assetLink.id);
                break;
            case AssetLinkUtil.TYPE_GENRE:
                openGenre(assetLink.id);
                break;
            case AssetLinkUtil.TYPE_YEAR:
                openYear(assetLink.id);
                break;
            default:
                Toast.makeText(activity, R.string.asset_link_error_unsupported, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void openSong(@NonNull String id) {
        MutableLiveData<Child> liveData = songRepository.getSong(id);
        Observer<Child> observer = new Observer<Child>() {
            @Override
            public void onChanged(Child child) {
                liveData.removeObserver(this);
                if (child == null) {
                    Toast.makeText(activity, R.string.asset_link_error_song, Toast.LENGTH_SHORT).show();
                    return;
                }
                SongBottomSheetViewModel viewModel = new ViewModelProvider(activity).get(SongBottomSheetViewModel.class);
                viewModel.setSong(child);
                SongBottomSheetDialog dialog = new SongBottomSheetDialog();
                Bundle args = new Bundle();
                args.putParcelable(Constants.TRACK_OBJECT, child);
                dialog.setArguments(args);
                dialog.show(activity.getSupportFragmentManager(), null);
            }
        };
        liveData.observe(activity, observer);
    }

    private void openAlbum(@NonNull String id) {
        MutableLiveData<AlbumID3> liveData = albumRepository.getAlbum(id);
        Observer<AlbumID3> observer = new Observer<AlbumID3>() {
            @Override
            public void onChanged(AlbumID3 album) {
                liveData.removeObserver(this);
                if (album == null) {
                    Toast.makeText(activity, R.string.asset_link_error_album, Toast.LENGTH_SHORT).show();
                    return;
                }
                Bundle args = new Bundle();
                args.putParcelable(Constants.ALBUM_OBJECT, album);
                navigateSafely(R.id.albumPageFragment, args);
            }
        };
        liveData.observe(activity, observer);
    }

    private void openArtist(@NonNull String id) {
        MutableLiveData<ArtistID3> liveData = artistRepository.getArtist(id);
        Observer<ArtistID3> observer = new Observer<ArtistID3>() {
            @Override
            public void onChanged(ArtistID3 artist) {
                liveData.removeObserver(this);
                if (artist == null) {
                    Toast.makeText(activity, R.string.asset_link_error_artist, Toast.LENGTH_SHORT).show();
                    return;
                }
                Bundle args = new Bundle();
                args.putParcelable(Constants.ARTIST_OBJECT, artist);
                navigateSafely(R.id.artistPageFragment, args);
            }
        };
        liveData.observe(activity, observer);
    }

    private void openPlaylist(@NonNull String id) {
        MutableLiveData<Playlist> liveData = playlistRepository.getPlaylist(id);
        Observer<Playlist> observer = new Observer<Playlist>() {
            @Override
            public void onChanged(Playlist playlist) {
                liveData.removeObserver(this);
                if (playlist == null) {
                    Toast.makeText(activity, R.string.asset_link_error_playlist, Toast.LENGTH_SHORT).show();
                    return;
                }
                Bundle args = new Bundle();
                args.putParcelable(Constants.PLAYLIST_OBJECT, playlist);
                navigateSafely(R.id.playlistPageFragment, args);
            }
        };
        liveData.observe(activity, observer);
    }

    private void openGenre(@NonNull String genreName) {
        String trimmed = genreName.trim();
        if (trimmed.isEmpty()) {
            Toast.makeText(activity, R.string.asset_link_error_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }

        Genre genre = new Genre();
        genre.setGenre(trimmed);
        genre.setSongCount(0);
        genre.setAlbumCount(0);
        Bundle args = new Bundle();
        args.putParcelable(Constants.GENRE_OBJECT, genre);
        args.putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE);
        navigateSafely(R.id.songListPageFragment, args);
    }

    private void openYear(@NonNull String yearValue) {
        try {
            int year = Integer.parseInt(yearValue.trim());
            Bundle args = new Bundle();
            args.putInt("year_object", year);
            args.putString(Constants.MEDIA_BY_YEAR, Constants.MEDIA_BY_YEAR);
            navigateSafely(R.id.songListPageFragment, args);
        } catch (NumberFormatException ex) {
            Toast.makeText(activity, R.string.asset_link_error_unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateSafely(int destinationId, @Nullable Bundle args) {
        activity.runOnUiThread(() -> {
            NavController navController = activity.navController;
            if (navController == null) {
                return;
            }
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == destinationId) {
                navController.navigate(destinationId, args, new NavOptions.Builder().setLaunchSingleTop(true).build());
            } else {
                navController.navigate(destinationId, args);
            }
        });
    }
}
