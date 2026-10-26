package com.eddyizm.tempus.ui.adapter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.databinding.ItemHorizontalDownloadBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.helper.recyclerview.FastScrollbar;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Util;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@UnstableApi
public class DownloadHorizontalAdapter extends RecyclerView.Adapter<DownloadHorizontalAdapter.ViewHolder> implements StandardViewTypeAdapter, FastScrollbar.BubbleTextGetter {
    private static final ExecutorService PLAYLIST_COVER_EXECUTOR = Executors.newSingleThreadExecutor();

    private final ClickCallback click;

    private String view;
    private String filterKey;
    private String filterValue;

    private List<Child> songs;
    private List<Child> shuffling;
    private List<Child> grouped;
    // Songs per row of the current view, by the row's group key. Counted once per list rather than
    // with a scan of every song on each bind.
    private Map<Object, Integer> groupCounts = Collections.emptyMap();

    // Main thread only. An empty string means the playlist has no cover of its own.
    private final Map<String, String> playlistCoverCache = new HashMap<>();
    private final Set<String> playlistCoverLookups = new HashSet<>();

    public DownloadHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.view = Constants.DOWNLOAD_TYPE_TRACK;
        this.songs = Collections.emptyList();
        this.grouped = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalDownloadBinding view = ItemHorizontalDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                initTrackLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                initAlbumLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                initArtistLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_GENRE:
                initGenreLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_YEAR:
                initYearLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                initPlaylistLayout(holder, position);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return grouped.size();
    }

    public void setItems(String view, String filterKey, String filterValue, List<Child> songs) {
        this.view = filterValue != null ? view : filterKey;
        this.filterKey = filterKey;
        this.filterValue = filterValue;

        this.songs = songs;

        List<Child> result = groupSong(songs);
        if (!Constants.DOWNLOAD_TYPE_TRACK.equals(this.view)) {
            sortByDisplayLabel(result);
        }
        this.grouped = result;
        this.groupCounts = countByGroup(songs);

        this.shuffling = shufflingSong(new ArrayList<>(songs));

        notifyDataSetChanged();
    }

    private List<Child> sortByDisplayLabel(List<Child> items) {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Comparator<Child> comparator = Comparator.comparing(this::getDisplayLabel, Comparator.nullsLast(collator));
        if (Constants.DOWNLOAD_TYPE_ALBUM.equals(this.view)) {
            comparator = comparator.thenComparing(Child::getAlbum, Comparator.nullsLast(collator));
        }
        items.sort(comparator);
        return items;
    }

    private String getDisplayLabel(Child item) {
        if (item == null) return null;

        String label;
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                String trackAlbumArtist = albumArtistOrNull(item);
                label = trackAlbumArtist != null ? trackAlbumArtist : item.getAlbum();
                if (label == null || label.trim().isEmpty()) {
                    label = item.getTitle();
                }
                break;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                String albumArtist = albumArtistOrNull(item);
                label = albumArtist != null ? albumArtist : item.getAlbum();
                break;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                label = item.getArtist();
                break;
            case Constants.DOWNLOAD_TYPE_GENRE:
                label = item.getGenre();
                break;
            case Constants.DOWNLOAD_TYPE_YEAR:
                label = item.getYear() == null ? null : item.getYear().toString();
                break;
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                label = item instanceof Download ? ((Download) item).getPlaylistName() : null;
                break;
            default:
                label = null;
                break;
        }

        return label == null || label.trim().isEmpty() ? null : label.trim();
    }

    @Override
    public String getTextToShowInBubble(int position) {
        if (position < 0 || position >= grouped.size()) return null;

        String label = getDisplayLabel(grouped.get(position));
        if (label == null) return null;

        int codePoint = label.codePointAt(0);
        if (!Character.isLetterOrDigit(codePoint)) return "#";
        return new String(Character.toChars(codePoint)).toUpperCase(Locale.getDefault());
    }

    public Child getItem(int id) {
        return grouped.get(id);
    }

    public List<Child> getShuffling() {
        return shuffling;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private List<Child> groupSong(List<Child> songs) {
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getId())).filter(Util.distinctByKey(Child::getId)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getAlbumId())).filter(Util.distinctByKey(Child::getAlbumId)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getArtistId())).filter(Util.distinctByKey(Child::getArtistId)).sorted(Comparator.comparing(Child::getArtist, Comparator.nullsLast(Comparator.naturalOrder()))).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_GENRE:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getGenre())).filter(Util.distinctByKey(Child::getGenre)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_YEAR:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getYear())).filter(Util.distinctByKey(Child::getYear)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                return filterSong(filterKey, filterValue, songs.stream()
                        .filter(song -> song instanceof Download && ((Download) song).getPlaylistId() != null)
                        .filter(Util.distinctByKey(song -> ((Download) song).getPlaylistId()))
                        .collect(Collectors.toList()));
        }

        return Collections.emptyList();
    }

    private List<Child> filterSong(String filterKey, String filterValue, List<Child> songs) {
        if (filterValue != null) {
            switch (filterKey) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    return songs.stream().filter(child -> child.getId().equals(filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    return songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_GENRE:
                    return songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_YEAR:
                    return songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    return songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_PLAYLIST:
                    return songs.stream().filter(child -> child instanceof Download && Objects.equals(((Download) child).getPlaylistId(), filterValue)).collect(Collectors.toList());
            }
        }

        return songs;
    }

    private List<Child> shufflingSong(List<Child> songs) {
        if (filterValue == null) {
            return songs;
        }

        switch (filterKey) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return songs.stream().filter(child -> child.getId().equals(filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_GENRE:
                return songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_YEAR:
                return songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                return songs.stream().filter(child -> child instanceof Download && Objects.equals(((Download) child).getPlaylistId(), filterValue)).collect(Collectors.toList());
            default:
                return songs;
        }
    }

    // The key a row of the given view groups its songs by, or null for a view of single songs.
    private static Function<Child, Object> groupKey(String view) {
        switch (view) {
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return Child::getAlbumId;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return Child::getArtistId;
            case Constants.DOWNLOAD_TYPE_GENRE:
                return Child::getGenre;
            case Constants.DOWNLOAD_TYPE_YEAR:
                return Child::getYear;
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                return song -> song instanceof Download ? ((Download) song).getPlaylistId() : null;
            default:
                return null;
        }
    }

    private Map<Object, Integer> countByGroup(List<Child> songs) {
        Function<Child, Object> key = groupKey(view);
        if (key == null) return Collections.emptyMap();

        Map<Object, Integer> counts = new HashMap<>();
        for (Child song : songs) {
            Object value = key.apply(song);
            if (value != null) counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    @VisibleForTesting
    int getGroupCount(int position) {
        Function<Child, Object> key = groupKey(view);
        if (key == null) return 0;

        Integer count = groupCounts.get(key.apply(grouped.get(position)));
        return count != null ? count : 0;
    }

    private void initTrackLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getTitle());
        holder.item.downloadedItemSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        holder.item.downloadedItemPreTextView.setText(song.getAlbum());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.VISIBLE);

        if (position > 0 && grouped.get(position - 1) != null && !sameAlbum(grouped.get(position - 1), grouped.get(position))) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private static String albumArtistOrNull(Child song) {
        if (song == null) return null;
        String albumArtist = song.getAlbumArtist();
        return albumArtist != null && !albumArtist.trim().isEmpty() ? albumArtist.trim() : null;
    }

    private static String albumArtistOrArtist(Child song) {
        String albumArtist = albumArtistOrNull(song);
        return albumArtist != null ? albumArtist : song.getArtist();
    }


    // Album ids are missing on some servers, and two nulls are not the same album.
    // Follows the sort in DownloadDao, which leads on the album artist and falls back to the album.
    private static boolean sameAlbumGroup(Child one, Child other) {
        String oneArtist = albumArtistOrNull(one);
        String otherArtist = albumArtistOrNull(other);

        if (oneArtist != null || otherArtist != null) {
            return Objects.equals(oneArtist, otherArtist);
        }

        return sameAlbum(one, other);
    }

    private static boolean sameAlbum(Child one, Child other) {
        if (one.getAlbumId() != null && other.getAlbumId() != null) {
            return Objects.equals(one.getAlbumId(), other.getAlbumId());
        }
        return Objects.equals(one.getAlbum(), other.getAlbum());
    }

    private void initAlbumLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getAlbum());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, String.valueOf(getGroupCount(position))));
        holder.item.downloadedItemPreTextView.setText(albumArtistOrArtist(song));

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.VISIBLE);

        if (position > 0 && grouped.get(position - 1) != null && !sameAlbumGroup(grouped.get(position - 1), grouped.get(position))) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private void initArtistLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getArtist());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, String.valueOf(getGroupCount(position))));

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initGenreLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getGenre());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, String.valueOf(getGroupCount(position))));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initYearLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(String.valueOf(song.getYear()));
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, String.valueOf(getGroupCount(position))));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initPlaylistLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);
        if (song instanceof Download) {
            Download download = (Download) song;
            holder.item.downloadedItemTitleTextView.setText(download.getPlaylistName());
            holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, String.valueOf(getGroupCount(position))));

            String playlistId = download.getPlaylistId();
            String coverArtId = playlistCoverCache.get(playlistId);

            if (coverArtId == null) {
                lookUpPlaylistCover(playlistId);
            }
            if (coverArtId == null || coverArtId.isEmpty()) {
                // The cover of the song itself, until or unless the playlist has one.
                coverArtId = download.getCoverArtId();
            }

            CustomGlideRequest.Builder
                    .from(holder.itemView.getContext(), coverArtId, CustomGlideRequest.ResourceType.Song)
                    .build()
                    .into(holder.item.itemCoverImageView);

            holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
            holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
            holder.item.divider.setVisibility(View.GONE);
        }
    }

    // Once per playlist, on one shared thread. A raw thread per bind used to start again on every
    // scroll past a playlist without a cover, as nothing recorded that the answer was none.
    private void lookUpPlaylistCover(String playlistId) {
        if (!playlistCoverLookups.add(playlistId)) return;

        Handler main = new Handler(Looper.getMainLooper());
        PLAYLIST_COVER_EXECUTOR.execute(() -> {
            String cover = AppDatabase.getInstance().playlistDao().getPlaylistCoverArtId(playlistId);
            main.post(() -> {
                playlistCoverCache.put(playlistId, cover != null ? cover : "");
                if (cover == null || !Constants.DOWNLOAD_TYPE_PLAYLIST.equals(view)) return;

                // Positions may have moved since the lookup started.
                for (int position = 0; position < grouped.size(); position++) {
                    Child row = grouped.get(position);
                    if (row instanceof Download && playlistId.equals(((Download) row).getPlaylistId())) {
                        notifyItemChanged(position);
                    }
                }
            });
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalDownloadBinding item;

        ViewHolder(ItemHorizontalDownloadBinding item) {
            super(item.getRoot());

            this.item = item;

            item.downloadedItemTitleTextView.setSelected(true);
            item.downloadedItemSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.downloadedItemMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();

            switch (view) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(grouped));
                    bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());
                    click.onMediaClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    bundle.putString(Constants.DOWNLOAD_TYPE_ALBUM, grouped.get(getBindingAdapterPosition()).getAlbumId());
                    click.onAlbumClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    bundle.putString(Constants.DOWNLOAD_TYPE_ARTIST, grouped.get(getBindingAdapterPosition()).getArtistId());
                    click.onArtistClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    bundle.putString(Constants.DOWNLOAD_TYPE_GENRE, grouped.get(getBindingAdapterPosition()).getGenre());
                    click.onGenreClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    bundle.putString(Constants.DOWNLOAD_TYPE_YEAR, grouped.get(getBindingAdapterPosition()).getYear().toString());
                    click.onYearClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_PLAYLIST:
                    if (grouped.get(getBindingAdapterPosition()) instanceof Download) {
                        bundle.putString(Constants.DOWNLOAD_TYPE_PLAYLIST, ((Download) grouped.get(getBindingAdapterPosition())).getPlaylistId());
                        click.onPlaylistClick(bundle);
                    }
                    break;
            }
        }

        private boolean onLongClick() {
            ArrayList<Child> filteredSongs = new ArrayList<>();

            Bundle bundle = new Bundle();

            switch (view) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    filteredSongs.add(grouped.get(getBindingAdapterPosition()));
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_ALBUM, grouped.get(getBindingAdapterPosition()).getAlbumId(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_ARTIST, grouped.get(getBindingAdapterPosition()).getArtistId(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_GENRE, grouped.get(getBindingAdapterPosition()).getGenre(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_YEAR, grouped.get(getBindingAdapterPosition()).getYear().toString(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_PLAYLIST:
                    if (grouped.get(getBindingAdapterPosition()) instanceof Download) {
                        filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_PLAYLIST, ((Download) grouped.get(getBindingAdapterPosition())).getPlaylistId(), songs));
                    }
                    break;
            }

            if (filteredSongs.isEmpty()) return false;

            bundle.putParcelableArrayList(Constants.DOWNLOAD_GROUP, new ArrayList<>(filteredSongs));
            bundle.putString(Constants.DOWNLOAD_GROUP_TITLE, item.downloadedItemTitleTextView.getText().toString());
            bundle.putString(Constants.DOWNLOAD_GROUP_SUBTITLE, item.downloadedItemSubtitleTextView.getText().toString());
            click.onDownloadGroupLongClick(bundle);

            return true;
        }
    }
}
