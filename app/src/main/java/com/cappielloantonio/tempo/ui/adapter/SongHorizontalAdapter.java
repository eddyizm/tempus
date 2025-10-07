package com.cappielloantonio.tempo.ui.adapter;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ItemHorizontalTrackBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.DiscTitle;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class SongHorizontalAdapter extends RecyclerView.Adapter<SongHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;
    private final boolean showCoverArt;
    private final boolean showAlbum;
    private final AlbumID3 album;

    private List<Child> songsFull;
    private List<Child> songs;
    private String currentFilter;

    private String currentPlayingId;
    private boolean isPlaying;
    private List<Integer> currentPlayingPositions = Collections.emptyList();
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Child> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(songsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                currentFilter = filterPattern;

                for (Child item : songsFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            songs = (List<Child>) results.values;
            notifyDataSetChanged();

            for (int pos : currentPlayingPositions) {
                if (pos >= 0 && pos < songs.size()) {
                    notifyItemChanged(pos, "payload_playback");
                }
            }
        }
    };

    public SongHorizontalAdapter(ClickCallback click, boolean showCoverArt, boolean showAlbum, AlbumID3 album) {
        this.click = click;
        this.showCoverArt = showCoverArt;
        this.showAlbum = showAlbum;
        this.songs = Collections.emptyList();
        this.songsFull = Collections.emptyList();
        this.currentFilter = "";
        this.album = album;
        setHasStableIds(false);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalTrackBinding view = ItemHorizontalTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("payload_playback")) {
            bindPlaybackState(holder, songs.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Child song = songs.get(position);

        holder.item.searchResultSongTitleTextView.setText(song.getTitle());

        holder.item.searchResultSongSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        this.showAlbum ?
                                song.getAlbum() :
                                song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        holder.item.trackNumberTextView.setText(MusicUtil.getReadableTrackNumber(holder.itemView.getContext(), song.getTrack()));

        if (DownloadUtil.getDownloadTracker(holder.itemView.getContext()).isDownloaded(song.getId())) {
            holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.VISIBLE);
        } else {
            holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.GONE);
        }

        if (showCoverArt) CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.songCoverImageView);

        holder.item.trackNumberTextView.setVisibility(showCoverArt ? View.INVISIBLE : View.VISIBLE);
        holder.item.songCoverImageView.setVisibility(showCoverArt ? View.VISIBLE : View.INVISIBLE);

        if (!showCoverArt &&
                (position == 0 ||
                        (position > 0 && songs.get(position - 1) != null &&
                                songs.get(position - 1).getDiscNumber() != null &&
                                songs.get(position).getDiscNumber() != null &&
                                songs.get(position - 1).getDiscNumber() < songs.get(position).getDiscNumber()
                        )
                )
        ) {
            holder.item.differentDiskDividerSector.setVisibility(View.VISIBLE);

            if (songs.get(position).getDiscNumber() != null && !Objects.requireNonNull(songs.get(position).getDiscNumber()).toString().isBlank()) {
                holder.item.discTitleTextView.setText(holder.itemView.getContext().getString(R.string.disc_titleless, songs.get(position).getDiscNumber().toString()));
            }

            if (album.getDiscTitles() != null) {
                Optional<DiscTitle> discTitle = album.getDiscTitles().stream().filter(title -> Objects.equals(title.getDisc(), songs.get(position).getDiscNumber())).findFirst();

                if (discTitle.isPresent() && discTitle.get().getDisc() != null && discTitle.get().getTitle() != null && !discTitle.get().getTitle().isEmpty()) {
                    holder.item.discTitleTextView.setText(holder.itemView.getContext().getString(R.string.disc_titlefull, discTitle.get().getDisc().toString() , discTitle.get().getTitle()));
                }
            }
        }

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

        bindPlaybackState(holder, song);
    }

    private void bindPlaybackState(@NonNull ViewHolder holder, @NonNull Child song) {
        boolean isCurrent = currentPlayingId != null && currentPlayingId.equals(song.getId());

        if (isCurrent) {
            holder.item.playPauseIcon.setVisibility(View.VISIBLE);
            if (isPlaying) {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_pause);
            } else {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_play);
            }
            if (!showCoverArt) {
                holder.item.trackNumberTextView.setVisibility(View.INVISIBLE);
            } else {
                holder.item.coverArtOverlay.setVisibility(View.VISIBLE);
            }
        } else {
            holder.item.playPauseIcon.setVisibility(View.INVISIBLE);
            if (!showCoverArt) {
                holder.item.trackNumberTextView.setVisibility(View.VISIBLE);
            } else {
                holder.item.coverArtOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void setItems(List<Child> songs) {
        this.songsFull = songs != null ? songs : Collections.emptyList();
        filtering.filter(currentFilter);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
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

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalTrackBinding item;

        ViewHolder(ItemHorizontalTrackBinding item) {
            super(item.getRoot());

            this.item = item;

            item.searchResultSongTitleTextView.setSelected(true);
            item.searchResultSongSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.searchResultSongMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            int pos = getBindingAdapterPosition();
            Child tappedSong = songs.get(pos);

            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(MusicUtil.limitPlayableMedia(songs, getBindingAdapterPosition())));
            bundle.putInt(Constants.ITEM_POSITION, MusicUtil.getPlayableMediaPosition(songs, getBindingAdapterPosition()));

            if (tappedSong.getId().equals(currentPlayingId)) {
                Log.i("SongHorizontalAdapter", "Tapping on currently playing song, toggling playback");
                try{
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    Log.i("SongHorizontalAdapter", "MediaBrowser retrieved, isPlaying: " + isPlaying);
                    if (isPlaying) {
                        mediaBrowser.pause();
                    } else {
                        mediaBrowser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e("SongHorizontalAdapter", "Error getting MediaBrowser", e);
                }
            } else {
                click.onMediaClick(bundle);
            }
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, songs.get(getBindingAdapterPosition()));

            click.onMediaLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        switch (order) {
            case Constants.MEDIA_BY_TITLE:
                songs.sort(Comparator.comparing(Child::getTitle));
                break;
            case Constants.MEDIA_MOST_RECENTLY_STARRED:
                songs.sort(Comparator.comparing(Child::getStarred, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case Constants.MEDIA_LEAST_RECENTLY_STARRED:
                songs.sort(Comparator.comparing(Child::getStarred, Comparator.nullsLast(Comparator.naturalOrder())));
                break;
        }

        notifyDataSetChanged();
    }

    public void setMediaBrowserListenableFuture(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }
}
