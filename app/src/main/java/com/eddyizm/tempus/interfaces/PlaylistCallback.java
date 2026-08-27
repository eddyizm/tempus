package com.eddyizm.tempus.interfaces;

import androidx.annotation.Keep;

@Keep
public interface PlaylistCallback {
    default void onDismiss() {}

    default void onRenamed(String name) {}
}
