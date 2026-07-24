package com.cappielloantonio.tempo.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogStarredArtistSyncBinding;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.StarredArtistsSyncViewModel;

import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class StarredArtistSyncDialog extends BaseSyncDialog {
    public StarredArtistSyncDialog(Runnable onCancel) {
        super(onCancel);
    }

    @Override
    protected int getDialogTitleResId() {
        return R.string.starred_artist_sync_dialog_title;
    }

    @Override
    protected View createDialogView(LayoutInflater layoutInflater) {
        DialogStarredArtistSyncBinding bind = DialogStarredArtistSyncBinding.inflate(layoutInflater);
        return bind.getRoot();
    }

    @Override
    protected void performSync(Context context, Runnable onDismiss) {
        StarredArtistsSyncViewModel viewModel = new ViewModelProvider(requireActivity()).get(StarredArtistsSyncViewModel.class);
        viewModel.getStarredArtistSongs(requireActivity()).observe(this, allSongs -> {
            if (allSongs != null && !allSongs.isEmpty()) {
                DownloadUtil.getDownloadTracker(context).download(
                        MappingUtil.mapDownloads(allSongs),
                        allSongs.stream().map(Download::new).collect(Collectors.toList())
                );
            }
            onDismiss.run();
        });
    }

    @Override
    protected void setSyncPreference(boolean enabled) {
        Preferences.setStarredArtistsSyncEnabled(enabled);
    }
}
