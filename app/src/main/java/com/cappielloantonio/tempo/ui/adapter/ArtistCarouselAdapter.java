package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.databinding.ItemArtistCarouselBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.TileSizeManager;

import java.util.Collections;
import java.util.List;

public class ArtistCarouselAdapter extends RecyclerView.Adapter<ArtistCarouselAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<ArtistID3> artists;
    private int sizePx = 400;

    public ArtistCarouselAdapter(ClickCallback click) {
        this.click = click;
        this.artists = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemArtistCarouselBinding view = ItemArtistCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        TileSizeManager.getInstance().calculateTileSize(parent.getContext());
        sizePx = TileSizeManager.getInstance().getTileSizePx(parent.getContext());
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ViewGroup.LayoutParams lp = holder.item.artistCoverImageView.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        holder.item.artistCoverImageView.setLayoutParams(lp);

        ArtistID3 artist = artists.get(position);

        holder.item.artistNameLabel.setText(artist.getName());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.item.artistCoverImageView);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    public void setItems(List<ArtistID3> artists) {
        this.artists = artists;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemArtistCarouselBinding item;

        ViewHolder(ItemArtistCarouselBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));
                click.onArtistClick(bundle);
            });

            itemView.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));
                click.onArtistLongClick(bundle);
                return true;
            });

            item.artistNameLabel.setSelected(true);
        }
    }
}
