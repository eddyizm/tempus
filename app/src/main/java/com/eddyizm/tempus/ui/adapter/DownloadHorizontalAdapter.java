package com.eddyizm.tempus.ui.adapter;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemHorizontalDownloadBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.helper.recyclerview.FastScrollbar;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.ExternalAudioReader;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.Util;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class DownloadHorizontalAdapter extends RecyclerView.Adapter<DownloadHorizontalAdapter.ViewHolder> implements StandardViewTypeAdapter, FastScrollbar.BubbleTextGetter {
    private final ClickCallback click;

    private String view;
    private String filterKey;
    private String filterValue;

    private List<Child> songs;
    private List<Child> shuffling;
    private List<Child> grouped;

    // Null while the filter is off.
    private String starOrder;

    private boolean showAlbumLine = true;

    private boolean showDownloadIndicator;

    private final java.util.Map<String, String> playlistCoverCache = new java.util.HashMap<>();

    public DownloadHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.view = Constants.DOWNLOAD_TYPE_TRACK;
        this.songs = Collections.emptyList();
        this.grouped = Collections.emptyList();
    }

    public boolean isStarFilterActive() {
        return starOrder != null;
    }

    public void setStarOrder(String starOrder) {
        this.starOrder = starOrder;
    }

    public void setShowAlbumLine(boolean showAlbumLine) {
        this.showAlbumLine = showAlbumLine;
    }

    public void setShowDownloadIndicator(boolean showDownloadIndicator) {
        this.showDownloadIndicator = showDownloadIndicator;
    }

    // The same two ways of telling a track is on the phone as the song list uses: the download
    // index while Tempus stores downloads itself, the files in the picked folder when it does not.
    private static boolean isOnPhone(Context context, Child song) {
        if (Preferences.getDownloadDirectoryUri() == null) {
            return DownloadUtil.getDownloadTracker(context).isDownloaded(song.getId());
        }
        return ExternalAudioReader.getUri(song) != null;
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
        if (isStarFilterActive()) {
            result = filterLiked(result);
        } else if (!Constants.DOWNLOAD_TYPE_TRACK.equals(this.view)) {
            sortByDisplayLabel(result);
        }
        this.grouped = result;

        List<Child> shuffleBase = shufflingSong(new ArrayList<>(songs));
        if (isStarFilterActive()) {
            shuffleBase = filterLiked(shuffleBase);
        }
        this.shuffling = shuffleBase;

        notifyDataSetChanged();
    }

    // The order of likes is read from the like date each track carries. For a group of several
    // tracks it is the newest like among them, so an album or artist only shows up once something
    // inside it has been liked, and takes the place of its newest like.
    private List<Child> filterLiked(List<Child> representatives) {
        Map<String, Date> likeByGroup = new HashMap<>();

        for (Child song : songs) {
            if (song.getStarred() == null) continue;

            String key = groupKeyOf(song);
            if (key == null) continue;

            Date starred = song.getStarred();
            Date known = likeByGroup.get(key);
            if (known == null || starred.after(known)) {
                likeByGroup.put(key, starred);
            }
        }

        Comparator<Child> byLike = Comparator.comparing(
                (Child child) -> likeByGroup.get(groupKeyOf(child)),
                Comparator.nullsLast(Comparator.<Date>naturalOrder())
        );

        return representatives.stream()
                .filter(child -> {
                    String key = groupKeyOf(child);
                    return key != null && likeByGroup.containsKey(key);
                })
                .sorted(byLike)
                .collect(Collectors.toList());
    }

    // The value that puts a track in its group under the current view, so a group's representative
    // can be matched against the tracks it was built from.
    private String groupKeyOf(Child child) {
        if (child == null) return null;

        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return child.getId();
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return child.getAlbumId();
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return child.getArtistId();
            case Constants.DOWNLOAD_TYPE_GENRE:
                return child.getGenre();
            case Constants.DOWNLOAD_TYPE_YEAR:
                return child.getYear() == null ? null : child.getYear().toString();
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                return child instanceof Download ? ((Download) child).getPlaylistId() : null;
            default:
                return null;
        }
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

    private String countSong(String filterKey, String filterValue, List<Child> songs) {
        if (filterValue != null) {
            switch (filterKey) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    return String.valueOf(songs.stream().filter(child -> child.getId().equals(filterValue)).count());
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).count());
                case Constants.DOWNLOAD_TYPE_GENRE:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).count());
                case Constants.DOWNLOAD_TYPE_YEAR:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).count());
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).count());
                case Constants.DOWNLOAD_TYPE_PLAYLIST:
                    return String.valueOf(songs.stream().filter(child -> child instanceof Download && Objects.equals(((Download) child).getPlaylistId(), filterValue)).count());
            }
        }

        return "0";
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

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.downloadedIndicatorFrame.setVisibility(
                showDownloadIndicator && isOnPhone(holder.itemView.getContext(), song)
                        ? View.VISIBLE
                        : View.GONE);

        // A flat list of tracks, like favorites, has no album to name: the line would only repeat
        // what the row above already said, and the divider it carries has no albums to split.
        if (!showAlbumLine) {
            holder.item.downloadedItemPreTextView.setText(null);
            holder.item.divider.setVisibility(View.GONE);
            return;
        }

        holder.item.downloadedItemPreTextView.setText(song.getAlbum());
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
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_ALBUM, song.getAlbumId(), songs)));
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
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_ARTIST, song.getArtistId(), songs)));

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
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_GENRE, song.getGenre(), songs)));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initYearLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(String.valueOf(song.getYear()));
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_YEAR, song.getYear().toString(), songs)));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initPlaylistLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);
        if (song instanceof Download) {
            Download download = (Download) song;
            holder.item.downloadedItemTitleTextView.setText(download.getPlaylistName());
            holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_PLAYLIST, download.getPlaylistId(), songs)));

            String playlistId = download.getPlaylistId();
            String coverArtId = playlistCoverCache.get(playlistId);

            if (coverArtId == null) {
                // Async lookup from local playlist cache
                new Thread(() -> {
                    String cachedCover = com.eddyizm.tempus.database.AppDatabase.getInstance().playlistDao().getPlaylistCoverArtId(playlistId);
                    if (cachedCover != null) {
                        playlistCoverCache.put(playlistId, cachedCover);
                        if (holder.itemView.getContext() instanceof android.app.Activity) {
                            ((android.app.Activity) holder.itemView.getContext()).runOnUiThread(() -> notifyItemChanged(position));
                        }
                    }
                }).start();
                // Fallback to track cover art while loading
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
