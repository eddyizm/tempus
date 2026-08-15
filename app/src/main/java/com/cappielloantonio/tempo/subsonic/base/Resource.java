package com.cappielloantonio.tempo.subsonic.base;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

/**
 * A small Loading / Success / Error wrapper for a repository result, so callers can render a
 * uniform "loading", "loaded" or "couldn't reach server" state instead of silently getting an
 * empty list when a request fails.
 *
 * @param <T> the success payload type
 */
@Keep
public class Resource<T> {
    public enum Status {LOADING, SUCCESS, ERROR}

    private final Status status;
    @Nullable
    private final T data;
    @Nullable
    private final NetworkError error;

    private Resource(Status status, @Nullable T data, @Nullable NetworkError error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null);
    }

    public static <T> Resource<T> success(@Nullable T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    public static <T> Resource<T> error(NetworkError error) {
        return new Resource<>(Status.ERROR, null, error);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public NetworkError getError() {
        return error;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
