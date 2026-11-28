package com.eddyizm.tempus.helper.recyclerview;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


public abstract class PaginationScrollListener extends RecyclerView.OnScrollListener {
    private final LinearLayoutManager layoutManager;

    protected PaginationScrollListener(LinearLayoutManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        int visibleItemCount = layoutManager.getChildCount();
        int totalItemCount = layoutManager.getItemCount();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

        if (canLoadMore() && !isLoading()) {
            if (firstVisibleItemPosition >= 0 && (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount / 4 * 3)) {
                loadMoreItems();
            }
        }
    }

    /** Override to stop requesting pages once the data source is exhausted. */
    protected boolean canLoadMore() {
        return true;
    }

    protected abstract void loadMoreItems();

    public abstract boolean isLoading();
}