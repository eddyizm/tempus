package com.eddyizm.tempus.subsonic.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class StringUtil {
    /**
     * Android keyboards capitalise the first letter of a URL field, so users end up with
     * {@code Https://host}. Retrofit/OkHttp lower-case the scheme themselves, but the cover-art
     * loader checks the string prefix and must see a lower-case scheme too.
     */
    public static String normalizeScheme(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd <= 0) return trimmed;
        return trimmed.substring(0, schemeEnd).toLowerCase(Locale.ROOT) + trimmed.substring(schemeEnd);
    }

    /** Scheme matching is intentionally case-insensitive: keyboards capitalise the first letter. */
    public static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public static String tokenize(String s) {
        final String MD5 = "MD5";
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance(MD5);
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & aMessageDigest));
                while (h.length() < 2) {
                    h.insert(0, "0");
                }
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}
