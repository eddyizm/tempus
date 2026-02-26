package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.databinding.ItemAlbumCarouselBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.util.Constants;

import java.util.Collections;
import java.util.List;

public class AlbumCarouselAdapter extends RecyclerView.Adapter<AlbumCarouselAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<AlbumID3> albums;
    private boolean showArtist;

    public AlbumCarouselAdapter(ClickCallback click, boolean showArtist) {
        this.click = click;
        this.albums = Collections.emptyList();
        this.showArtist = showArtist;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumCarouselBinding view = ItemAlbumCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlbumID3 album = albums.get(position);

        holder.item.albumNameLabel.setText(album.getName());
        holder.item.artistNameLabel.setText(album.getArtist());
        holder.item.artistNameLabel.setVisibility(showArtist ? View.VISIBLE : View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void setItems(List<AlbumID3> albums) {
        this.albums = albums;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemAlbumCarouselBinding item;

        ViewHolder(ItemAlbumCarouselBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));
                click.onAlbumClick(bundle);
            });

            itemView.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));
                click.onAlbumLongClick(bundle);
                return true;
            });

            item.albumNameLabel.setSelected(true);
            item.artistNameLabel.setSelected(true);
        }
    }
}
