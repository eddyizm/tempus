package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

public class ClickablePreferenceCategory extends PreferenceCategory {
    public interface OnClickListener {
        void onClick(ClickablePreferenceCategory category);
    }

    private OnClickListener clickListener;

    public ClickablePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(true);
    }

    public void setOnClickListener(OnClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(this);
            }
        });
    }
}
