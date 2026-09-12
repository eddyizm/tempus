package com.eddyizm.tempus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemHomeContinueListeningBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;

public class ContinueListeningAdapter extends RecyclerView.Adapter<ContinueListeningAdapter.ViewHolder> {
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private List<Preferences.ResumePoint> items = Collections.emptyList();

    public ContinueListeningAdapter(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }

    /** The MediaBrowser future may not exist yet at construction; refresh it once available. */
    public void setMediaBrowserFuture(ListenableFuture<MediaBrowser> future) {
        this.mediaBrowserListenableFuture = future;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeContinueListeningBinding binding =
                ItemHomeContinueListeningBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(List<Preferences.ResumePoint> resumePoints) {
        this.items = resumePoints;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemHomeContinueListeningBinding binding;

        ViewHolder(ItemHomeContinueListeningBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos < 0 || pos >= items.size()) return;
                resume(items.get(pos));
            });
        }

        void bind(Preferences.ResumePoint resumePoint) {
            binding.continueListeningTitleLabel.setText(resumePoint.getTitle());
            binding.continueListeningAlbumLabel.setText(resumePoint.getAlbum());
            binding.continueListeningResumeLabel.setText(
                    itemView.getContext().getString(R.string.home_continue_listening_resume_at, formatPosition(resumePoint.getPosition()))
            );

            // Artwork via the same CustomGlideRequest path the album/artist adapters use
            // (resolves coverArtId to a server getCoverArt URL), else the placeholder.
            if (resumePoint.getCoverArtId() != null) {
                CustomGlideRequest.Builder
                        .from(itemView.getContext(), resumePoint.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                        .build()
                        .into(binding.continueListeningCoverImageView);
            } else {
                binding.continueListeningCoverImageView.setImageResource(R.drawable.ic_placeholder_album);
            }
        }

        private void resume(Preferences.ResumePoint resumePoint) {
            if (mediaBrowserListenableFuture == null) return;
            String albumId = resumePoint.getAlbumId();
            if (albumId == null) {
                playSingle(resumePoint);
                return;
            }
            // Resume the track, then keep going through the rest of the album/book (DSub-style).
            new AlbumRepository().getAlbumTracks(albumId, tracks -> {
                if (tracks == null || tracks.isEmpty()) {
                    playSingle(resumePoint);
                    return;
                }
                int index = 0;
                long position = 0;
                for (int i = 0; i < tracks.size(); i++) {
                    if (resumePoint.getId().equals(tracks.get(i).getId())) {
                        index = i;
                        position = resumePoint.getPosition();
                        break;
                    }
                }
                MediaManager.startQueue(mediaBrowserListenableFuture, tracks, index, position);
            });
        }

        private void playSingle(Preferences.ResumePoint resumePoint) {
            // A minimal Child with just the id is enough to stream; pass the saved position
            // explicitly so only an explicit resume seeks, never a normal queue start.
            Child child = new Child(resumePoint.getId());
            child.setTitle(resumePoint.getTitle());
            child.setAlbum(resumePoint.getAlbum());
            child.setArtist(resumePoint.getArtist());
            child.setAlbumId(resumePoint.getAlbumId());
            child.setCoverArtId(resumePoint.getCoverArtId());
            MediaManager.startQueue(mediaBrowserListenableFuture, Collections.singletonList(child), 0, resumePoint.getPosition());
        }
    }

    private static String formatPosition(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
}
