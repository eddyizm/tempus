package com.cappielloantonio.tempo.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogStarredAlbumSyncBinding;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.StarredAlbumsSyncViewModel;

import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class StarredAlbumSyncDialog extends BaseSyncDialog {
    public StarredAlbumSyncDialog(Runnable onCancel) {
        super(onCancel);
    }

    @Override
    protected int getDialogTitleResId() {
        return R.string.starred_album_sync_dialog_title;
    }

    @Override
    protected View createDialogView(LayoutInflater layoutInflater) {
        DialogStarredAlbumSyncBinding bind = DialogStarredAlbumSyncBinding.inflate(layoutInflater);
        return bind.getRoot();
    }

    @Override
    protected void performSync(Context context, Runnable onDismiss) {
        StarredAlbumsSyncViewModel viewModel = new ViewModelProvider(requireActivity()).get(StarredAlbumsSyncViewModel.class);
        viewModel.getStarredAlbumSongs(requireActivity()).observe(this, allSongs -> {
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
        Preferences.setStarredAlbumsSyncEnabled(enabled);
    }
}
