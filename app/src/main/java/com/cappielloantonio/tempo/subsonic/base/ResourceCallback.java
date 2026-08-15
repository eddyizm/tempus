package com.cappielloantonio.tempo.subsonic.base;

import androidx.annotation.Keep;

/**
 * Delivers a repository result to a consumer. Implementations are typically called on the main
 * thread (Retrofit's callback executor), so they may update the UI directly.
 *
 * @param <T> the success payload type
 */
@Keep
public interface ResourceCallback<T> {
    void onResult(Resource<T> resource);
}
