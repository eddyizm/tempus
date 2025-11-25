package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ItemHorizontalDownloadBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.DownloadStack;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UnstableApi
public class DownloadHorizontalAdapter extends RecyclerView.Adapter<DownloadHorizontalAdapter.ViewHolder> {
    private final ClickCallback click;

    private String view;
    private List<Child> filtered;
    private List<Child> grouped;
    private List<Integer> counts;

    public DownloadHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.view = Constants.DOWNLOAD_TYPE_TRACK;
        this.grouped = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalDownloadBinding view = ItemHorizontalDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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
        }
    }

    @Override
    public int getItemCount() {
        return grouped.size();
    }

    public void setItems(String view, List<DownloadStack> filters, List<Child> songs) {
        this.view = view;

        this.filtered = filterSongs(filters, songs).collect(Collectors.toList());
        this.grouped = new ArrayList<>();

        Function<Child, String> grouping = getGrouping(view);
        Map<String, Integer> mapping = new HashMap<>();
        for (Child song : filtered) {
            String key = grouping.apply(song);
            Integer prev = mapping.putIfAbsent(key, 1);
            if (prev != null) {
                mapping.put(key, prev + 1);
            } else {
                grouped.add(song);
            }
        }
        this.counts = grouped.stream().map(song -> mapping.get(grouping.apply(song))).collect(Collectors.toList());
        notifyDataSetChanged();
    }

    public Child getItem(int id) {
        return grouped.get(id);
    }

    public List<Child> getFiltered() {
        return filtered;
    }

    private static Function<Child, String> getGrouping(String key) {
        switch (key) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return Child::getId;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return Child::getAlbumId;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return Child::getArtistId;
            case Constants.DOWNLOAD_TYPE_GENRE:
                return  Child::getGenre;
            case Constants.DOWNLOAD_TYPE_YEAR:
                return child -> Objects.toString(child.getYear(), "");
            default:
                throw new IllegalArgumentException();
        }
    }

    private Stream<Child> filterSongs(List<DownloadStack> filters, List<Child> songs) {
        Stream<Child> stream = songs.stream();
        for (DownloadStack s : filters) {
            if (s.getView() == null) continue;
            Function<Child, String> grouping = getGrouping(s.getId());
            stream = stream.filter(c -> Objects.equals(grouping.apply(c), s.getView()));
        }
        return stream;
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

        if (position > 0 && grouped.get(position - 1) != null && !Objects.equals(grouped.get(position - 1).getAlbum(), grouped.get(position).getAlbum())) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private void initAlbumLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getAlbum());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext()
                .getString(R.string.download_item_single_subtitle_formatter, counts.get(position).toString()));
        holder.item.downloadedItemPreTextView.setText(song.getArtist());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.VISIBLE);

        if (position > 0 && grouped.get(position - 1) != null && !Objects.equals(grouped.get(position - 1).getArtist(), grouped.get(position).getArtist())) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private void initArtistLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getArtist());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext()
                .getString(R.string.download_item_single_subtitle_formatter, counts.get(position).toString()));

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
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext()
                .getString(R.string.download_item_single_subtitle_formatter, counts.get(position).toString()));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initYearLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(String.valueOf(song.getYear()));
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext()
                .getString(R.string.download_item_single_subtitle_formatter, counts.get(position).toString()));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
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
            if (view.equals(Constants.DOWNLOAD_TYPE_TRACK)) {
                bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(grouped));
                bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());
            } else {
                bundle.putString(view, getGrouping(view).apply(grouped.get(getBindingAdapterPosition())));
            }

            switch (view) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    click.onMediaClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    click.onAlbumClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    click.onArtistClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    click.onGenreClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    click.onYearClick(bundle);
                    break;
            }
        }

        private boolean onLongClick() {
            List<Child> filteredSongs;
            if (view.equals(Constants.DOWNLOAD_TYPE_TRACK)) {
                filteredSongs = List.of(grouped.get(getBindingAdapterPosition()));
            } else {
                String id = getGrouping(view).apply(grouped.get(getBindingAdapterPosition()));
                filteredSongs = filterSongs(List.of(new DownloadStack(view, id)), filtered).collect(Collectors.toList());
            }
            if (filteredSongs.isEmpty()) return false;

            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.DOWNLOAD_GROUP, new ArrayList<>(filteredSongs));
            bundle.putString(Constants.DOWNLOAD_GROUP_TITLE, item.downloadedItemTitleTextView.getText().toString());
            bundle.putString(Constants.DOWNLOAD_GROUP_SUBTITLE, item.downloadedItemSubtitleTextView.getText().toString());
            click.onDownloadGroupLongClick(bundle);
            return true;
        }
    }
}
