package com.cappielloantonio.tempo.subsonic.base;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

/**
 * A categorised request failure. The category lets the UI tell apart the cases a user (and a
 * hardened login flow, see #829) needs to act on differently: wrong credentials, an unreachable
 * server, and a server that answered with something unexpected.
 */
@Keep
public class NetworkError {
    public enum Type {
        /** No usable response: connection refused, timeout, DNS/TLS failure, airplane mode. */
        UNREACHABLE,
        /** A non-2xx HTTP response. */
        SERVER_ERROR,
        /** A 2xx response whose body was missing or could not be parsed. */
        INVALID_RESPONSE,
        /** Subsonic replied "failed" with a credential/authorization error code. */
        AUTH_FAILED,
        /** Subsonic replied "failed" with any other error code. */
        API_ERROR
    }

    private final Type type;
    @Nullable
    private final String message;
    @Nullable
    private final Integer code;

    public NetworkError(Type type, @Nullable String message, @Nullable Integer code) {
        this.type = type;
        this.message = message;
        this.code = code;
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    /** The Subsonic error code, or the HTTP status code, when one is available. */
    @Nullable
    public Integer getCode() {
        return code;
    }

    public boolean isAuthFailure() {
        return type == Type.AUTH_FAILED;
    }
}
