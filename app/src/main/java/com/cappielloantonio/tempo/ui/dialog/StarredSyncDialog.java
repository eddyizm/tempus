package com.cappielloantonio.tempo.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogStarredSyncBinding;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.StarredSyncViewModel;

import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class StarredSyncDialog extends BaseSyncDialog {
    public StarredSyncDialog(Runnable onCancel) {
        super(onCancel);
    }

    @Override
    protected int getDialogTitleResId() {
        return R.string.starred_sync_dialog_title;
    }

    @Override
    protected View createDialogView(LayoutInflater layoutInflater) {
        DialogStarredSyncBinding bind = DialogStarredSyncBinding.inflate(layoutInflater);
        return bind.getRoot();
    }

    @Override
    protected void performSync(Context context, Runnable onDismiss) {
        StarredSyncViewModel viewModel = new ViewModelProvider(requireActivity()).get(StarredSyncViewModel.class);
        viewModel.getStarredTracks(requireActivity()).observe(this, songs -> {
            if (songs != null && !songs.isEmpty()) {
                DownloadUtil.getDownloadTracker(context).download(
                        MappingUtil.mapDownloads(songs),
                        songs.stream().map(Download::new).collect(Collectors.toList())
                );
            }
            onDismiss.run();
        });
    }

    @Override
    protected void setSyncPreference(boolean enabled) {
        Preferences.setStarredSyncEnabled(enabled);
    }
}
