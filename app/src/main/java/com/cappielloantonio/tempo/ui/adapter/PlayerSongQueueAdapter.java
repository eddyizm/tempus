package com.cappielloantonio.tempo.ui.adapter;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestBuilder;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ItemPlayerQueueSongBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.interfaces.MediaIndexCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PlayerSongQueueAdapter extends RecyclerView.Adapter<PlayerSongQueueAdapter.ViewHolder> {
    private static final String TAG = "PlayerSongQueueAdapter";
    private final ClickCallback click;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private List<Child> songs;

    private String currentPlayingId;
    private boolean isPlaying;
    private List<Integer> currentPlayingPositions = Collections.emptyList();

    public PlayerSongQueueAdapter(ClickCallback click) {
        this.click = click;
        this.songs = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerQueueSongBinding view = ItemPlayerQueueSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child song = songs.get(holder.getLayoutPosition());

        holder.item.queueSongTitleTextView.setText(song.getTitle());
        holder.item.queueSongSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        RequestBuilder<Drawable> thumbnail = CustomGlideRequest.Builder
                        .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .sizeMultiplier(0.1f);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .thumbnail(thumbnail)
                .into(holder.item.queueSongCoverImageView);

        MediaManager.getCurrentIndex(mediaBrowserListenableFuture, new MediaIndexCallback() {
            @Override
            public void onRecovery(int index) {
                if (holder.getLayoutPosition() < index) {
                    holder.item.queueSongTitleTextView.setAlpha(0.2f);
                    holder.item.queueSongSubtitleTextView.setAlpha(0.2f);
                    holder.item.ratingIndicatorImageView.setAlpha(0.2f);
                } else {
                    holder.item.queueSongTitleTextView.setAlpha(1.0f);
                    holder.item.queueSongSubtitleTextView.setAlpha(1.0f);
                    holder.item.ratingIndicatorImageView.setAlpha(1.0f);
                }
            }
        });

        if (Preferences.showItemRating()) {
            if (song.getStarred() == null && song.getUserRating() == null) {
                holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
            }

            holder.item.preferredIcon.setVisibility(song.getStarred() != null ? View.VISIBLE : View.GONE);
            holder.item.ratingBarLayout.setVisibility(song.getUserRating() != null ? View.VISIBLE : View.GONE);

            if (song.getUserRating() != null) {
                holder.item.oneStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 1 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.twoStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 2 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.threeStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 3 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fourStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 4 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fiveStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 5 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
            }
        } else {
            holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    int pos = holder.getBindingAdapterPosition();
                    Child s = songs.get(pos);
                    if (currentPlayingId != null && currentPlayingId.equals(s.getId())) {
                        if (isPlaying) {
                            mediaBrowser.pause();
                        } else {
                            mediaBrowser.play();
                        }
                    } else {
                        mediaBrowser.seekTo(pos, 0);
                        mediaBrowser.play();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error obtaining MediaBrowser", e);
                }
            }, MoreExecutors.directExecutor());

        });
        bindPlaybackState(holder, song);
    }

    private void bindPlaybackState(@NonNull PlayerSongQueueAdapter.ViewHolder holder, @NonNull Child song) {
        boolean isCurrent = currentPlayingId != null && currentPlayingId.equals(song.getId());

        if (isCurrent) {
            holder.item.playPauseIcon.setVisibility(View.VISIBLE);
            if (isPlaying) {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_pause);
            } else {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_play);
            }
            holder.item.coverArtOverlay.setVisibility(View.VISIBLE);
        } else {
            holder.item.playPauseIcon.setVisibility(View.INVISIBLE);
            holder.item.coverArtOverlay.setVisibility(View.INVISIBLE);
        }
    }

    public List<Child> getItems() {
        return this.songs;
    }

    public void setItems(List<Child> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (songs == null) {
            return 0;
        }
        return songs.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setMediaBrowserListenableFuture(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }

    public void setPlaybackState(String mediaId, boolean playing) {
        String oldId = this.currentPlayingId;
        boolean oldPlaying = this.isPlaying;
        List<Integer> oldPositions = currentPlayingPositions;

        this.currentPlayingId = mediaId;
        this.isPlaying = playing;

        if (Objects.equals(oldId, mediaId) && oldPlaying == playing) {
            List<Integer> newPositionsCheck = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();
            if (oldPositions.equals(newPositionsCheck)) {
                return;
            }
        }

        currentPlayingPositions = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();

        for (int pos : oldPositions) {
            if (pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
        for (int pos : currentPlayingPositions) {
            if (!oldPositions.contains(pos) && pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
    }

    private List<Integer> findPositionsById(String id) {
        if (id == null) return Collections.emptyList();
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (id.equals(songs.get(i).getId())) {
                positions.add(i);
            }
        }
        return positions;
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemPlayerQueueSongBinding item;

        ViewHolder(ItemPlayerQueueSongBinding item) {
            super(item.getRoot());

            this.item = item;

            item.queueSongTitleTextView.setSelected(true);
            item.queueSongSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));
            bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());

            click.onMediaClick(bundle);
        }
    }
}
