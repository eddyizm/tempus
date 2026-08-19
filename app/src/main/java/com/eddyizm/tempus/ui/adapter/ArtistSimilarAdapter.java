package com.eddyizm.tempus.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemLibrarySimilarArtistBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.SimilarArtistID3;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.TileSizeManager;

import java.util.Collections;
import java.util.List;

public class ArtistSimilarAdapter extends RecyclerView.Adapter<ArtistSimilarAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<SimilarArtistID3> artists;

    private int sizePx = 400;

    public ArtistSimilarAdapter(ClickCallback click) {
        this.click = click;
        this.artists = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibrarySimilarArtistBinding view = ItemLibrarySimilarArtistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);

        TileSizeManager.getInstance().calculateTileSize(parent.getContext());
        sizePx = TileSizeManager.getInstance().getTileSizePx(parent.getContext());

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ViewGroup.LayoutParams lp = holder.item.similarArtistCoverImageView.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        holder.item.similarArtistCoverImageView.setLayoutParams(lp);

        SimilarArtistID3 artist = artists.get(position);

        holder.item.artistNameLabel.setText(artist.getName());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.item.similarArtistCoverImageView);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    public SimilarArtistID3 getItem(int position) {
        return artists.get(position);
    }

    public void setItems(List<SimilarArtistID3> artists) {
        this.artists = artists;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibrarySimilarArtistBinding item;

        ViewHolder(ItemLibrarySimilarArtistBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.artistNameLabel.setSelected(true);
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistLongClick(bundle);

            return true;
        }
    }
}
