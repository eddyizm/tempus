package com.cappielloantonio.tempo.ui.adapter;

import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ItemArtistReleaseSectionBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class AlbumSectionsAdapter extends RecyclerView.Adapter<AlbumSectionsAdapter.ViewHolder> {
    private final android.content.Context context;
    private final ClickCallback click;
    private ItemArtistReleaseSectionBinding view;
    private Map<String, List<AlbumID3>> albumMap;
    private final BiMap<String, String> typeTitles = HashBiMap.create();
    private final List<String> orderTypes = List.of("album", "ep", "single", "compilation", "soundtrack", "live", "remix");

    public AlbumSectionsAdapter(android.content.Context context, ClickCallback click) {
        this.click = click;
        this.context = context;
        this.albumMap = Collections.emptyMap();
        this.view = null;

        this.typeTitles.put("album", context.getString(R.string.artist_page_title_album_section));
        this.typeTitles.put("ep", context.getString(R.string.artist_page_title_ep_section));
        this.typeTitles.put("single", context.getString(R.string.artist_page_title_single_section));
        this.typeTitles.put("compilation", context.getString(R.string.artist_page_title_compilation_section));
        this.typeTitles.put("soundtrack", context.getString(R.string.artist_page_title_soundtrack_section));
        this.typeTitles.put("live", context.getString(R.string.artist_page_title_live_section));
        this.typeTitles.put("remix", context.getString(R.string.artist_page_title_remix_section));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.view = ItemArtistReleaseSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(this.view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Set<String> typeNames = albumMap.keySet();

        String typeName = typeNames.stream()
                .sorted()
                .sorted(Comparator.comparingInt(orderTypes::indexOf))
                .sorted(Comparator.comparing((String item) -> !orderTypes.contains(item)))
                .toArray()[position].toString();
        String sectionTitle = this.typeTitles.containsKey(typeName) ? this.typeTitles.get(typeName) : Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
        holder.item.typeTitle.setText(sectionTitle);

        List<AlbumID3> albums = albumMap.get(typeName);

        holder.item.mainAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(this.context, LinearLayoutManager.HORIZONTAL, false));
        holder.item.mainAlbumsRecyclerView.setHasFixedSize(true);
        holder.item.mainAlbumsRecyclerView.setNestedScrollingEnabled(false);

        AlbumCarouselAdapter mainAlbumAdapter = new AlbumCarouselAdapter(this.click, false);
        holder.item.mainAlbumsRecyclerView.setAdapter(mainAlbumAdapter);

        if (holder.item != null && albums != null) {
            holder.item.mainAlbumsSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
            mainAlbumAdapter.setItems(albums);
        }
    }

    @Override
    public int getItemCount() {
        return albumMap.size();
    }

    public void setItems(Map<String, List<AlbumID3>> albumMap) {
        this.albumMap = albumMap;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemArtistReleaseSectionBinding item;

        ViewHolder(ItemArtistReleaseSectionBinding item) {
            super(item.getRoot());
            this.item = item;

            item.mainAlbumsSeeAllTextView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                String sectionTitle = this.item.typeTitle.getText().toString();
                bundle.putString(Constants.ALBUM_LIST_TITLE, sectionTitle);

                String typeName = typeTitles.containsValue(sectionTitle) ? typeTitles.inverse().get(sectionTitle) : sectionTitle.toLowerCase();
                bundle.putParcelableArrayList(Constants.ALBUMS_OBJECT, new ArrayList<>(albumMap.get(typeName)));
                Navigation.findNavController(v).navigate(R.id.albumListPageFragment, bundle);
            });
        }
    }
}
