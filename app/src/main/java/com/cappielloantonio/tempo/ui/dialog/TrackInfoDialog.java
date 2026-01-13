package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.MediaMetadata;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogTrackInfoBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class TrackInfoDialog extends DialogFragment {
    private DialogTrackInfoBinding bind;

    private final MediaMetadata mediaMetadata;
    private AssetLinkUtil.AssetLink songLink;
    private AssetLinkUtil.AssetLink albumLink;
    private AssetLinkUtil.AssetLink artistLink;
    private AssetLinkUtil.AssetLink genreLink;
    private AssetLinkUtil.AssetLink yearLink;

    public TrackInfoDialog(MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        bind = DialogTrackInfoBinding.inflate(getLayoutInflater());

        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(bind.getRoot())
                .setPositiveButton(R.string.track_info_dialog_positive_button, (dialog, id) -> dialog.cancel())
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        setTrackInfo();
        setTrackTranscodingInfo();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void setTrackInfo() {
        genreLink = null;
        yearLink = null;
        
        String type = mediaMetadata.extras != null ? mediaMetadata.extras.getString("type") : null;
        boolean isRadio = Objects.equals(type, Constants.MEDIA_TYPE_RADIO);
        
        if (isRadio) {
            // For radio: always read from extras first (radioArtist, radioTitle, stationName)
            // MediaMetadata.title/artist are formatted for notification
            String stationName = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("stationName",
                    mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "")
                    : mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "";
            
            String artist = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("radioArtist", "")
                    : "";
            
            String title = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("radioTitle", "")
                    : "";
            
            // Format: "Artist - Song" or fallback to title or station name
            String mainTitle;
            if (!android.text.TextUtils.isEmpty(artist) && !android.text.TextUtils.isEmpty(title)) {
                mainTitle = artist + " - " + title;
            } else if (!android.text.TextUtils.isEmpty(title)) {
                mainTitle = title;
            } else if (!android.text.TextUtils.isEmpty(artist)) {
                mainTitle = artist;
            } else {
                mainTitle = stationName;
            }
            
            bind.trakTitleInfoTextView.setText(mainTitle);
            bind.trakArtistInfoTextView.setText(stationName);
        } else {
            bind.trakTitleInfoTextView.setText(mediaMetadata.title);
            bind.trakArtistInfoTextView.setText(
                    mediaMetadata.artist != null
                            ? mediaMetadata.artist
                            : "");
        }

        if (mediaMetadata.extras != null) {
            songLink = AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_SONG, mediaMetadata.extras.getString("id"));
            albumLink = AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_ALBUM, mediaMetadata.extras.getString("albumId"));
            artistLink = AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_ARTIST, mediaMetadata.extras.getString("artistId"));
            genreLink = AssetLinkUtil.parseLinkString(mediaMetadata.extras.getString("assetLinkGenre"));
            yearLink = AssetLinkUtil.parseLinkString(mediaMetadata.extras.getString("assetLinkYear"));

            CustomGlideRequest.Builder
                    .from(requireContext(), mediaMetadata.extras.getString("coverArtId", ""), CustomGlideRequest.ResourceType.Song)
                    .build()
                    .into(bind.trackCoverInfoImageView);

            bindAssetLink(bind.trackCoverInfoImageView, albumLink != null ? albumLink : songLink);
            bindAssetLink(bind.trakTitleInfoTextView, songLink);
            bindAssetLink(bind.trakArtistInfoTextView, artistLink != null ? artistLink : songLink);

            String titleValue = mediaMetadata.extras.getString("title", getString(R.string.label_placeholder));
            String albumValue = mediaMetadata.extras.getString("album", getString(R.string.label_placeholder));
            String artistValue = mediaMetadata.extras.getString("artist", getString(R.string.label_placeholder));
            String genreValue = mediaMetadata.extras.getString("genre", getString(R.string.label_placeholder));
            int yearValue = mediaMetadata.extras.getInt("year", 0);
            
            // Handle radio-specific metadata
            if (isRadio) {
                String stationName = mediaMetadata.extras.getString("stationName", getString(R.string.label_placeholder));
                String radioArtist = mediaMetadata.extras.getString("radioArtist", "");
                String radioTitle = mediaMetadata.extras.getString("radioTitle", "");
                
                // Show station name in station section
                bind.stationInfoSector.setVisibility(android.view.View.VISIBLE);
                bind.stationValueSector.setText(stationName);
                
                // Use radio metadata for title/artist if available
                if (!android.text.TextUtils.isEmpty(radioTitle)) {
                    titleValue = radioTitle;
                }
                if (!android.text.TextUtils.isEmpty(radioArtist)) {
                    artistValue = radioArtist;
                }
            } else {
                bind.stationInfoSector.setVisibility(android.view.View.GONE);
            }

            if (genreLink == null && genreValue != null && !genreValue.isEmpty() && !getString(R.string.label_placeholder).contentEquals(genreValue)) {
                genreLink = AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_GENRE, genreValue);
            }

            if (yearLink == null && yearValue != 0) {
                yearLink = AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_YEAR, String.valueOf(yearValue));
            }

            bind.titleValueSector.setText(titleValue);
            bind.albumValueSector.setText(albumValue);
            bind.artistValueSector.setText(artistValue);
            bind.trackNumberValueSector.setText(mediaMetadata.extras.getInt("track", 0) != 0 ? String.valueOf(mediaMetadata.extras.getInt("track", 0)) : getString(R.string.label_placeholder));
            bind.yearValueSector.setText(yearValue != 0 ? String.valueOf(yearValue) : getString(R.string.label_placeholder));
            bind.genreValueSector.setText(genreValue);
            bind.sizeValueSector.setText(mediaMetadata.extras.getLong("size", 0) != 0 ? MusicUtil.getReadableByteCount(mediaMetadata.extras.getLong("size", 0)) : getString(R.string.label_placeholder));
            bind.contentTypeValueSector.setText(mediaMetadata.extras.getString("contentType", getString(R.string.label_placeholder)));
            bind.suffixValueSector.setText(mediaMetadata.extras.getString("suffix", getString(R.string.label_placeholder)));
            bind.transcodedContentTypeValueSector.setText(mediaMetadata.extras.getString("transcodedContentType", getString(R.string.label_placeholder)));
            bind.transcodedSuffixValueSector.setText(mediaMetadata.extras.getString("transcodedSuffix", getString(R.string.label_placeholder)));
            bind.durationValueSector.setText(mediaMetadata.extras.getInt("duration", 0) != 0 ? MusicUtil.getReadableDurationString(mediaMetadata.extras.getInt("duration", 0), false) : getString(R.string.label_placeholder));
            bind.bitrateValueSector.setText(mediaMetadata.extras.getInt("bitrate", 0) != 0 ? mediaMetadata.extras.getInt("bitrate", 0) + " kbps" : getString(R.string.label_placeholder));
            bind.samplingRateValueSector.setText(mediaMetadata.extras.getInt("samplingRate", 0) != 0 ? mediaMetadata.extras.getInt("samplingRate", 0) + " Hz" : getString(R.string.label_placeholder));
            bind.bitDepthValueSector.setText(mediaMetadata.extras.getInt("bitDepth", 0) != 0 ? mediaMetadata.extras.getInt("bitDepth", 0) + " bits" : getString(R.string.label_placeholder));
            bind.pathValueSector.setText(mediaMetadata.extras.getString("path", getString(R.string.label_placeholder)));
            bind.discNumberValueSector.setText(mediaMetadata.extras.getInt("discNumber", 0) != 0 ? String.valueOf(mediaMetadata.extras.getInt("discNumber", 0)) : getString(R.string.label_placeholder));

            bindAssetLink(bind.titleValueSector, songLink);
            bindAssetLink(bind.albumValueSector, albumLink);
            bindAssetLink(bind.artistValueSector, artistLink);
            bindAssetLink(bind.genreValueSector, genreLink);
            bindAssetLink(bind.yearValueSector, yearLink);
        }
    }

    private void setTrackTranscodingInfo() {
        StringBuilder info = new StringBuilder();

        boolean prioritizeServerTranscoding = Preferences.isServerPrioritized();

        String transcodingExtension = MusicUtil.getTranscodingFormatPreference();
        String transcodingBitrate = Integer.parseInt(MusicUtil.getBitratePreference()) != 0 ? Integer.parseInt(MusicUtil.getBitratePreference()) + "kbps" : "Original";

        if (mediaMetadata.extras != null && mediaMetadata.extras.getString("uri", "").contains(Constants.DOWNLOAD_URI)) {
            info.append(getString(R.string.track_info_summary_downloaded_file));

            bind.trakTranscodingInfoTextView.setText(info);
            return;
        }

        if (prioritizeServerTranscoding) {
            info.append(getString(R.string.track_info_summary_server_prioritized));

            bind.trakTranscodingInfoTextView.setText(info);
            return;
        }

        if (!prioritizeServerTranscoding && transcodingExtension.equals("raw") && transcodingBitrate.equals("Original")) {
            info.append(getString(R.string.track_info_summary_original_file));

            bind.trakTranscodingInfoTextView.setText(info);
            return;
        }

        if (!prioritizeServerTranscoding && !transcodingExtension.equals("raw") && transcodingBitrate.equals("Original")) {
            info.append(getString(R.string.track_info_summary_transcoding_codec, transcodingExtension));

            bind.trakTranscodingInfoTextView.setText(info);
            return;
        }

        if (!prioritizeServerTranscoding && transcodingExtension.equals("raw") && !transcodingBitrate.equals("Original")) {
            info.append(getString(R.string.track_info_summary_transcoding_bitrate, transcodingBitrate));

            bind.trakTranscodingInfoTextView.setText(info);
            return;
        }

        if (!prioritizeServerTranscoding && !transcodingExtension.equals("raw") && !transcodingBitrate.equals("Original")) {
            info.append(getString(R.string.track_info_summary_full_transcode, transcodingExtension, transcodingBitrate));

            bind.trakTranscodingInfoTextView.setText(info);
        }
    }

    private void bindAssetLink(android.view.View view, AssetLinkUtil.AssetLink assetLink) {
        if (view == null) return;
        if (assetLink == null) {
            AssetLinkUtil.clearLinkAppearance(view);
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            view.setClickable(false);
            view.setLongClickable(false);
            return;
        }

        view.setClickable(true);
        view.setLongClickable(true);
        AssetLinkUtil.applyLinkAppearance(view);
        view.setOnClickListener(v -> {
            dismissAllowingStateLoss();
            boolean collapse = !AssetLinkUtil.TYPE_SONG.equals(assetLink.type);
            ((com.cappielloantonio.tempo.ui.activity.MainActivity) requireActivity()).openAssetLink(assetLink, collapse);
        });
        view.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, assetLink.id), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

}
