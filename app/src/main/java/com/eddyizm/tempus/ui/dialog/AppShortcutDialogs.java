package com.eddyizm.tempus.ui.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemAppShortcutBinding;
import com.eddyizm.tempus.util.AppShortcuts;
import com.eddyizm.tempus.util.AssetLinkUtil;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The dialogs for the playlists and albums the user picked as launcher shortcuts. */
public final class AppShortcutDialogs {
    private AppShortcutDialogs() {
    }

    /**
     * Adds a pick. When every place is taken, asks which pick it replaces instead of refusing,
     * since the user may not remember which ones are there.
     */
    public static void add(@NonNull Context context, @NonNull Preferences.ShortcutItem item, @NonNull Runnable onChanged) {
        if (AppShortcuts.addFixed(context, item)) {
            onChanged.run();
            return;
        }
        List<Preferences.ShortcutItem> fixed = Preferences.getFixedShortcuts();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shortcut_replace_title)
                .setItems(labelsOf(context, fixed), (dialog, which) -> {
                    AppShortcuts.replaceFixed(context, which, item);
                    onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Lists the picks to reorder by their handle and remove by their bin. Nothing changes until
     * Save, so Cancel undoes a removal.
     */
    public static void manage(@NonNull Context context, @NonNull Runnable onChanged) {
        List<Preferences.ShortcutItem> fixed = Preferences.getFixedShortcuts();
        if (fixed.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.settings_shortcuts_manage)
                    .setMessage(R.string.settings_shortcuts_manage_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        RecyclerView list = new RecyclerView(context);
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding, padding, 0);
        list.setLayoutManager(new LinearLayoutManager(context));
        PicksAdapter adapter = new PicksAdapter(fixed);
        list.setAdapter(adapter);
        ItemTouchHelper dragger = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                Collections.swap(adapter.items, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // Only the handle drags, so scrolling the list never moves a row.
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        dragger.attachToRecyclerView(list);
        adapter.dragger = dragger;

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_shortcuts_manage)
                .setView(list)
                .setPositiveButton(R.string.shortcut_manage_save, (dialog, which) -> {
                    AppShortcuts.keepFixed(context, adapter.items);
                    onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class PicksAdapter extends RecyclerView.Adapter<PicksAdapter.ViewHolder> {
        final List<Preferences.ShortcutItem> items;
        ItemTouchHelper dragger;

        PicksAdapter(@NonNull List<Preferences.ShortcutItem> items) {
            this.items = new ArrayList<>(items);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemAppShortcutBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Preferences.ShortcutItem item = items.get(position);
            holder.bind.appShortcutTitle.setText(item.getName() != null ? item.getName() : item.getId());
            holder.bind.appShortcutType.setText(typeOf(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAppShortcutBinding bind;

            @SuppressLint("ClickableViewAccessibility")
            ViewHolder(@NonNull ItemAppShortcutBinding bind) {
                super(bind.getRoot());
                this.bind = bind;
                bind.appShortcutDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragger != null) dragger.startDrag(this);
                    return false;
                });
                bind.appShortcutRemove.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    items.remove(position);
                    notifyItemRemoved(position);
                });
            }
        }
    }

    @StringRes
    private static int typeOf(@NonNull Preferences.ShortcutItem item) {
        return AssetLinkUtil.TYPE_ALBUM.equals(item.getType()) ? R.string.shortcut_type_album : R.string.shortcut_type_playlist;
    }

    /** Each pick's name, with whether it is a playlist or an album in smaller type below. */
    @NonNull
    private static CharSequence[] labelsOf(@NonNull Context context, @NonNull List<Preferences.ShortcutItem> items) {
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Preferences.ShortcutItem item = items.get(i);
            String name = item.getName() != null ? item.getName() : item.getId();
            String type = context.getString(typeOf(item));
            SpannableString label = new SpannableString(name + "\n" + type);
            label.setSpan(new RelativeSizeSpan(0.8f), name.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            labels[i] = label;
        }
        return labels;
    }
}
