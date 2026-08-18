package com.eddyizm.tempus.interfaces;

import androidx.annotation.Keep;

@Keep

public interface PodcastCallback {
    default void onDismiss() {}
}
