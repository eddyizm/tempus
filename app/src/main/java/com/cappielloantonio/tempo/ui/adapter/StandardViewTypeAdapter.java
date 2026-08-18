package com.cappielloantonio.tempo.ui.adapter;

/**
 * Interface to ensure a standard ViewType is used for performance,
 * enabling proper RecyclerView view recycling.
 */
public interface StandardViewTypeAdapter {
    /**
     * Returns a constant view type to allow view recycling.
     */
    default int getItemViewType(int position) {
        return 0;
    }
}
