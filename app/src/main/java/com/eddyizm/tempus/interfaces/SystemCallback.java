package com.eddyizm.tempus.interfaces;

import androidx.annotation.Keep;

@Keep
public interface SystemCallback {
    default void onError(Exception exception) {}
    default void onSuccess(String password, String token, String salt) {}

    // The request failed at the transport. Defaults to onError, so a caller that does not care
    // which of the two happened behaves as it did before.
    default void onNetworkFailure(Exception exception) {
        onError(exception);
    }
}
