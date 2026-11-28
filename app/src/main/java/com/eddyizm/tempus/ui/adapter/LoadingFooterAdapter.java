package com.eddyizm.tempus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;

/**
 * A single optional trailing row shown while another page is being fetched.
 * Kept separate from {@link SongHorizontalAdapter} so other screens are unaffected.
 */
public class LoadingFooterAdapter extends RecyclerView.Adapter<LoadingFooterAdapter.ViewHolder> {
    private boolean visible = false;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song_list_loading, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // The row is only an indeterminate progress indicator; nothing to bind.
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        if (visible) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
