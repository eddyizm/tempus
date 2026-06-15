package com.cappielloantonio.tempo.ui.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.cappielloantonio.tempo.subsonic.models.Child;
import java.util.List;

public class SongDiffCallback extends DiffUtil.Callback {
    private final List<Child> oldList;
    private final List<Child> newList;

    public SongDiffCallback(List<Child> oldList, List<Child> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        // Since Child is Parcelable, maybe check fields if needed, but ID equality + list change is usually enough for RecyclerView
        return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }
}
