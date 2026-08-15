package com.cappielloantonio.tempo.subsonic.base;

import androidx.annotation.NonNull;

import com.cappielloantonio.tempo.subsonic.models.ErrorCode;
import com.cappielloantonio.tempo.subsonic.models.ResponseStatus;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Shared Retrofit callback that maps every failure mode of a Subsonic call — transport failure,
 * non-2xx status, unparseable body, and a "failed" Subsonic response — into a single
 * {@link Resource} in one place, instead of each repository re-implementing an anonymous
 * {@code Callback} whose {@code onFailure} is usually empty.
 *
 * Subclasses only pull the payload they care about out of a successful response via
 * {@link #extractData(SubsonicResponse)}.
 *
 * @param <T> the success payload type
 */
public abstract class SubsonicCallback<T> implements Callback<ApiResponse> {
    private final ResourceCallback<T> callback;

    protected SubsonicCallback(ResourceCallback<T> callback) {
        this.callback = callback;
    }

    /** Extract the payload from a successful response. May return null when there is no payload. */
    protected abstract T extractData(SubsonicResponse response);

    @Override
    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
        if (!response.isSuccessful()) {
            callback.onResult(Resource.error(new NetworkError(
                    NetworkError.Type.SERVER_ERROR, "HTTP " + response.code(), response.code())));
            return;
        }

        try {
            ApiResponse body = response.body();
            SubsonicResponse subsonicResponse = body != null ? body.getSubsonicResponse() : null;

            if (subsonicResponse == null) {
                callback.onResult(Resource.error(new NetworkError(
                        NetworkError.Type.INVALID_RESPONSE, "Empty server response", null)));
                return;
            }

            if (ResponseStatus.FAILED.equals(subsonicResponse.getStatus())) {
                callback.onResult(Resource.error(toNetworkError(subsonicResponse)));
                return;
            }

            callback.onResult(Resource.success(extractData(subsonicResponse)));
        } catch (Exception e) {
            callback.onResult(Resource.error(new NetworkError(
                    NetworkError.Type.INVALID_RESPONSE, e.getMessage(), null)));
        }
    }

    @Override
    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
        callback.onResult(Resource.error(new NetworkError(
                NetworkError.Type.UNREACHABLE, t.getMessage(), null)));
    }

    private static NetworkError toNetworkError(SubsonicResponse response) {
        Integer code = response.getError() != null ? response.getError().getCode() : null;
        String message = response.getError() != null ? response.getError().getMessage() : null;

        NetworkError.Type type = isAuthCode(code) ? NetworkError.Type.AUTH_FAILED : NetworkError.Type.API_ERROR;
        return new NetworkError(type, message, code);
    }

    private static boolean isAuthCode(Integer code) {
        return code != null && (code == ErrorCode.WRONG_USERNAME_OR_PASSWORD
                || code == ErrorCode.TOKEN_AUTHENTICATION_NOT_SUPPORTED
                || code == ErrorCode.USER_NOT_AUTHORIZED);
    }
}
