package com.eddyizm.tempus.subsonic;

import com.eddyizm.tempus.subsonic.utils.StringUtil;

import java.util.Locale;
import java.util.UUID;

public class SubsonicPreferences {
    private String serverUrl;
    private String username;
    private String clientName = "Tempus";
    private SubsonicAuthentication authentication;

    public String getServerUrl() {
        return serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getClientName() {
        return clientName;
    }

    public SubsonicAuthentication getAuthentication() {
        return authentication;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = normalizeScheme(serverUrl);
    }

    /**
     * Android keyboards capitalise the first letter of a URL field, so users end up with
     * {@code Https://host}. Retrofit/OkHttp lower-case the scheme themselves, but the cover-art
     * loader checks the string prefix and must see a lower-case scheme too.
     */
    private static String normalizeScheme(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd <= 0) return trimmed;
        return trimmed.substring(0, schemeEnd).toLowerCase(Locale.ROOT) + trimmed.substring(schemeEnd);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public void setAuthentication(String password, String token, String salt, boolean isLowSecurity) {
        if (password != null) {
            this.authentication = new SubsonicAuthentication(password, isLowSecurity);
        }

        if (token != null && salt != null) {
            this.authentication = new SubsonicAuthentication(token, salt);
        }
    }

    public static class SubsonicAuthentication {
        private String password;
        private String salt;
        private String token;

        public SubsonicAuthentication(String password, boolean isLowSecurity) {
            if (isLowSecurity) {
                this.password = password;
            } else {
                update(password);
            }
        }

        public SubsonicAuthentication(String token, String salt) {
            this.token = token;
            this.salt = salt;
        }

        public String getPassword() {
            return password;
        }

        public String getSalt() {
            return salt;
        }

        public String getToken() {
            return token;
        }

        void update(String password) {
            this.salt = UUID.randomUUID().toString();
            this.token = StringUtil.tokenize(password + salt);
        }
    }
}
