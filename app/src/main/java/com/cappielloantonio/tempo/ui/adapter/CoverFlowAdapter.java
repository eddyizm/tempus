package com.cappielloantonio.tempo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.model.Cover;

import org.jspecify.annotations.NonNull;

import java.util.List;

public class CoverFlowAdapter extends RecyclerView.Adapter<CoverFlowAdapter.CoverViewHolder> {

    /** Callback that is invoked for every bound item – useful for the extra Glide request. */
    public interface OnCoverBoundListener {
        void onCoverBound(@NonNull Cover cover, @NonNull ImageView coverImage);
    }

    private final Context context;
    private final List<Cover> covers;
    private final OnCoverBoundListener boundListener;

    public CoverFlowAdapter(@NonNull Context context,
                            @NonNull List<Cover> covers,
                            @NonNull OnCoverBoundListener boundListener) {
        this.context = context;
        this.covers  = covers;
        this.boundListener = boundListener;
    }

    @NonNull
    @Override
    public CoverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate a simple item layout that contains only an ImageView.
        View view = LayoutInflater.from(context).inflate(R.layout.item_cover_flow, parent, false);
        return new CoverViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CoverViewHolder holder, int position) {
        Cover cover = covers.get(position);
        holder.bind(cover);
        // Let the fragment (or caller) run the extra CustomGlideRequest if it needs the id.
        boundListener.onCoverBound(cover, holder.coverImage);
    }

    @Override
    public int getItemCount() {
        return covers.size();
    }

    /** -----------------------------------------------------------------
     *  ViewHolder – each item holds a single ImageView.
     * ----------------------------------------------------------------- */
    class CoverViewHolder extends RecyclerView.ViewHolder {
        final ImageView coverImage;

        CoverViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.item_cover_image);
        }

        void bind(@NonNull Cover cover) {
            // Normal image loading (the flow thumbnails)
            Glide.with(context)
                    .load(cover.getUrl())
                    .centerCrop()
                    .into(coverImage);
        }
    }
}

