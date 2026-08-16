package com.eddyizm.tempus.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemHorizontalHomeSectorBinding;
import com.eddyizm.tempus.databinding.ItemHorizontalPlaylistDialogTrackBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;

import java.util.Collections;
import java.util.List;

public class HomeSectorHorizontalAdapter extends RecyclerView.Adapter<HomeSectorHorizontalAdapter.ViewHolder> {
    private List<HomeSector> sectors;

    public HomeSectorHorizontalAdapter() {
        this.sectors = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalHomeSectorBinding view = ItemHorizontalHomeSectorBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        HomeSector sector = sectors.get(position);

        holder.item.homeSectorTitleCheckBox.setText(sector.getSectorTitle());
        holder.item.homeSectorTitleCheckBox.setChecked(sector.isVisible());
    }

    @Override
    public int getItemCount() {
        return sectors.size();
    }

    public List<HomeSector> getItems() {
        return this.sectors;
    }

    public void setItems(List<HomeSector> sectors) {
        this.sectors = sectors;
        notifyDataSetChanged();
    }

    public HomeSector getItem(int id) {
        return sectors.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalHomeSectorBinding item;

        ViewHolder(ItemHorizontalHomeSectorBinding item) {
            super(item.getRoot());

            this.item = item;

            this.item.homeSectorTitleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> onCheck(isChecked));
        }

        private void onCheck(boolean isChecked) {
            sectors.get(getBindingAdapterPosition()).setVisible(isChecked);
        }
    }
}
