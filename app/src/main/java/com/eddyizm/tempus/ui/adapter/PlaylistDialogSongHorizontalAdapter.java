package com.eddyizm.tempus.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemHorizontalPlaylistDialogTrackBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.MusicUtil;

import java.util.Collections;
import java.util.List;

public class PlaylistDialogSongHorizontalAdapter extends RecyclerView.Adapter<PlaylistDialogSongHorizontalAdapter.ViewHolder> {
    public interface Listener {
        void onRemove(int position);

        void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);
    }

    private final Listener listener;
    private List<Child> songs;

    public PlaylistDialogSongHorizontalAdapter(Listener listener) {
        this.listener = listener;
        this.songs = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalPlaylistDialogTrackBinding view = ItemHorizontalPlaylistDialogTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child song = songs.get(position);

        holder.item.playlistDialogSongTitleTextView.setText(song.getTitle());
        holder.item.playlistDialogAlbumArtistTextView.setText(song.getArtist());
        holder.item.playlistDialogSongDurationTextView.setText(MusicUtil.getReadableDurationString(song.getDuration(), false));

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.playlistDialogSongCoverImageView);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public List<Child> getItems() {
        return this.songs;
    }

    public void setItems(List<Child> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalPlaylistDialogTrackBinding item;

        @SuppressLint("ClickableViewAccessibility")
        ViewHolder(ItemHorizontalPlaylistDialogTrackBinding item) {
            super(item.getRoot());

            this.item = item;

            item.playlistDialogSongTitleTextView.setSelected(true);

            item.playlistDialogSongRemoveButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onRemove(position);
            });

            item.playlistDialogSongHandleButton.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onStartDrag(this);
                return false;
            });
        }
    }
}
