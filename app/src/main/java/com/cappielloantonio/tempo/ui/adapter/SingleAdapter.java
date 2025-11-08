package com.cappielloantonio.tempo.ui.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.function.Consumer;
import java.util.function.Function;

public class SingleAdapter<Bind extends ViewBinding>
        extends RecyclerView.Adapter<SingleAdapter<Bind>.ViewHolder> {
    private Function<ViewGroup, Bind> createBindCB;
    private Consumer<ViewHolder> bindViewHolderCB;
    private boolean display = false;

    public SingleAdapter(Function<ViewGroup, Bind> createBindCB,
                         Consumer<ViewHolder> bindViewHolderCB) {
        this.createBindCB = createBindCB;
        this.bindViewHolderCB = bindViewHolderCB;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(createBindCB.apply(parent));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bindViewHolderCB.accept(holder);
    }

    @Override
    public int getItemCount() {
        return display ? 1 : 0;
    }

    public void setDisplay(boolean display) {
        this.display = display;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public Bind item;
        ViewHolder(Bind item) {
            super(item.getRoot());
            this.item = item;
        }
    }
}
