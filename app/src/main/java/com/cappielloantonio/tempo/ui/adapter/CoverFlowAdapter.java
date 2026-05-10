package com.cappielloantonio.tempo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cappielloantonio.tempo.R;

import org.jspecify.annotations.NonNull;

import java.util.List;

public class CoverFlowAdapter extends RecyclerView.Adapter<CoverFlowAdapter.ViewHolder> {

    private final List<String> imageUrls;
    private final Context ctx;
    private final LayoutInflater inflater;

    public CoverFlowAdapter(Context ctx, List<String> imageUrls) {
        this.ctx = ctx;
        this.imageUrls = imageUrls;
        this.inflater = LayoutInflater.from(ctx);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_cover_flow, parent, false);
        return new ViewHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = imageUrls.get(position);
        Glide.with(ctx)
                .load(url)
                .placeholder(R.drawable.ui_empty_list)
                .into(holder.cover);
    }

    @Override public int getItemCount() {
        return imageUrls.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        ViewHolder(View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.imgCover);
        }
    }
}
