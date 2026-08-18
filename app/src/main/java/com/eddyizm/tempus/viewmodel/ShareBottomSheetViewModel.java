package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.eddyizm.tempus.repository.SharingRepository;
import com.eddyizm.tempus.subsonic.models.Share;

public class ShareBottomSheetViewModel extends AndroidViewModel {
    private final SharingRepository sharingRepository;

    private Share share;

    public ShareBottomSheetViewModel(@NonNull Application application) {
        super(application);

        sharingRepository = new SharingRepository();
    }

    public Share getShare() {
        return share;
    }

    public void setShare(Share share) {
        this.share = share;
    }

    public void updateShare(String description, long expires) {
        sharingRepository.updateShare(share.getId(), description, expires);
    }

    public void deleteShare() {
        sharingRepository.deleteShare(share.getId());
    }
}
