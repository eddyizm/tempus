package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;

import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MusicUtil {
    private static final String TAG = "MusicUtil";

    private static final Pattern BITRATE_PATTERN = Pattern.compile("&maxBitRate=\\d+");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("&format=\\w+");

    public static Uri getStreamUri(String id, int timeOffset) {
        Map<String, String> params = App.getSubsonicClientInstance(false).getParams();

        StringBuilder uri = new StringBuilder();

        uri.append(App.getSubsonicClientInstance(false).getUrl());
        uri.append("stream");

        if (params.containsKey("u") && params.get("u") != null)
            uri.append("?u=").append(Util.encode(params.get("u")));
        if (params.containsKey("p") && params.get("p") != null)
            uri.append("&p=").append(params.get("p"));
        if (params.containsKey("s") && params.get("s") != null)
            uri.append("&s=").append(params.get("s"));
        if (params.containsKey("t") && params.get("t") != null)
            uri.append("&t=").append(params.get("t"));
        if (params.containsKey("v") && params.get("v") != null)
            uri.append("&v=").append(params.get("v"));
        if (params.containsKey("c") && params.get("c") != null)
            uri.append("&c=").append(params.get("c"));

        String selectedBitrate = getBitratePreference();
        String selectedFormat = getTranscodingFormatPreference();
        Log.i(TAG, "DEBUG: Requesting Format: " + selectedFormat + " at Bitrate: " + selectedBitrate);
        
        if (!Preferences.isServerPrioritized())
            uri.append("&maxBitRate=").append(getBitratePreference());
        if (!Preferences.isServerPrioritized())
            uri.append("&format=").append(getTranscodingFormatPreference());
        if (Preferences.askForEstimateContentLength())
            uri.append("&estimateContentLength=true");
        if (timeOffset > 0)
            uri.append("&timeOffset=").append(timeOffset);

        uri.append("&id=").append(id);

        Log.d(TAG, "getStreamUri: " + uri);

        return Uri.parse(uri.toString());
    }

    public static Uri getStreamUri(String id) {
        return getStreamUri(id, 0);
    }

    public static Uri updateStreamUri(Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        // If it is local (content:// or file://), return it IMMEDIATELY.
        // This prevents the code below from appending &maxBitRate to a local path.
        if (scheme != null && (scheme.equals("content") || scheme.equals("file"))) {
            return uri;
        }
        
        String s = uri.toString();

        Matcher m1 = BITRATE_PATTERN.matcher(s);
        s = m1.replaceAll("");
        Matcher m2 = FORMAT_PATTERN.matcher(s);
        s = m2.replaceAll("");
        s = s.replace("&estimateContentLength=true", "");

        if (!Preferences.isServerPrioritized())
            s += "&maxBitRate=" + getBitratePreference();
        if (!Preferences.isServerPrioritized())
            s += "&format=" + getTranscodingFormatPreference();
        if (Preferences.askForEstimateContentLength())
            s += "&estimateContentLength=true";

        return Uri.parse(s);
    }

    public static Uri getDownloadUri(String id) {
        StringBuilder uri = new StringBuilder();

        Download download = new DownloadRepository().getDownload(id);

        if (download == null || download.getDownloadUri().isEmpty()) {
            Map<String, String> params = App.getSubsonicClientInstance(false).getParams();

            uri.append(App.getSubsonicClientInstance(false).getUrl());
            uri.append("download");

            if (params.containsKey("u") && params.get("u") != null)
                uri.append("?u=").append(Util.encode(params.get("u")));
            if (params.containsKey("p") && params.get("p") != null)
                uri.append("&p=").append(params.get("p"));
            if (params.containsKey("s") && params.get("s") != null)
                uri.append("&s=").append(params.get("s"));
            if (params.containsKey("t") && params.get("t") != null)
                uri.append("&t=").append(params.get("t"));
            if (params.containsKey("v") && params.get("v") != null)
                uri.append("&v=").append(params.get("v"));
            if (params.containsKey("c") && params.get("c") != null)
                uri.append("&c=").append(params.get("c"));

            uri.append("&id=").append(id);
        } else {
            uri.append(download.getDownloadUri());
        }

        Log.d(TAG, "getDownloadUri: " + uri);

        return Uri.parse(uri.toString());
    }

    public static Uri getTranscodedDownloadUri(String id) {
        Map<String, String> params = App.getSubsonicClientInstance(false).getParams();

        StringBuilder uri = new StringBuilder();

        uri.append(App.getSubsonicClientInstance(false).getUrl());
        uri.append("stream");

        if (params.containsKey("u") && params.get("u") != null)
            uri.append("?u=").append(Util.encode(params.get("u")));
        if (params.containsKey("p") && params.get("p") != null)
            uri.append("&p=").append(params.get("p"));
        if (params.containsKey("s") && params.get("s") != null)
            uri.append("&s=").append(params.get("s"));
        if (params.containsKey("t") && params.get("t") != null)
            uri.append("&t=").append(params.get("t"));
        if (params.containsKey("v") && params.get("v") != null)
            uri.append("&v=").append(params.get("v"));
        if (params.containsKey("c") && params.get("c") != null)
            uri.append("&c=").append(params.get("c"));

        if (!Preferences.isServerPrioritizedInTranscodedDownload())
            uri.append("&maxBitRate=").append(getBitratePreferenceForDownload());
        if (!Preferences.isServerPrioritizedInTranscodedDownload())
            uri.append("&format=").append(getTranscodingFormatPreferenceForDownload());

        uri.append("&id=").append(id);

        Log.d(TAG, "getTranscodedDownloadUri: " + uri);

        return Uri.parse(uri.toString());
    }

    public static String getReadableDurationString(Long duration, boolean millis) {
        long lenght = duration != null ? duration : 0;

        long minutes;
        long seconds;

        if (millis) {
            minutes = (lenght / 1000) / 60;
            seconds = (lenght / 1000) % 60;
        } else {
            minutes = lenght / 60;
            seconds = lenght % 60;
        }

        if (minutes < 60) {
            return String.format(Locale.getDefault(), "%01d:%02d", minutes, seconds);
        } else {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    public static String getReadableDurationString(Integer duration, boolean millis) {
        long lenght = duration != null ? duration : 0;
        return getReadableDurationString(lenght, millis);
    }

    public static String getReadableAudioQualityString(Child child) {
        if (!Preferences.showAudioQuality() || child.getBitrate() == null) return "";

        return "•" +
                " " +
                child.getBitrate() +
                "kbps" +
                " • " +
                (child.getBitDepth() != null && child.getBitDepth() != 0
                        ? child.getBitDepth() + "/" + (child.getSamplingRate() != null ? child.getSamplingRate() / 1000 : "")
                        : (child.getSamplingRate() != null
                        ? new DecimalFormat("0.#").format(child.getSamplingRate() / 1000.0) + "kHz"
                        : "")) +
                " " +
                child.getSuffix();
    }

    public static String getReadablePodcastDurationString(long duration) {
        long minutes = duration / 60;

        if (minutes < 60) {
            return String.format(Locale.getDefault(), "%01d min", minutes);
        } else {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.getDefault(), "%d h %02d min", hours, minutes);
        }
    }

    public static String getReadableTrackNumber(Context context, Integer trackNumber) {
        if (trackNumber != null) {
            return String.valueOf(trackNumber);
        }

        return context.getString(R.string.label_placeholder);
    }

    public static String getReadableString(String string) {
        if (string != null) {
            return Html.fromHtml(string, Html.FROM_HTML_MODE_COMPACT).toString();
        }

        return "";
    }

    public static String forceReadableString(String string) {
        if (string != null) {
            return getReadableString(string)
                    .replaceAll("&#34;", "\"")
                    .replaceAll("&#39;", "'")
                    .replaceAll("&amp;", "'")
                    .replaceAll("<a\\s+([^>]+)>((?:.(?!</a>))*.)</a>", "");
        }

        return "";
    }

    public static String getReadableLyrics(String string) {
        if (string != null) {
            return string
                    .replaceAll("&#34;", "\"")
                    .replaceAll("&#39;", "'")
                    .replaceAll("&amp;", "'")
                    .replaceAll("&#xA;", "\n");
        }

        return "";
    }

    public static String getReadableByteCount(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);

        if (absB < 1024) {
            return bytes + " B";
        }

        long value = absB;

        CharacterIterator ci = new StringCharacterIterator("KMGTPE");

        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }

        value *= Long.signum(bytes);

        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    public static String passwordHexEncoding(String plainPassword) {
        return "enc:" + plainPassword.chars().mapToObj(Integer::toHexString).collect(Collectors.joining());
    }

    public static String getBitratePreference() {
        Network network = getConnectivityManager().getActiveNetwork();
        NetworkCapabilities networkCapabilities = getConnectivityManager().getNetworkCapabilities(network);
        String audioTranscodeFormat = getTranscodingFormatPreference();

        if (audioTranscodeFormat.equals("raw") || network == null || networkCapabilities == null)
            return "0";

        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return Preferences.getMaxBitrateWifi();
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return Preferences.getMaxBitrateMobile();
        } else {
            return Preferences.getMaxBitrateWifi();
        }
    }

    public static String getTranscodingFormatPreference() {
        Network network = getConnectivityManager().getActiveNetwork();
        NetworkCapabilities networkCapabilities = getConnectivityManager().getNetworkCapabilities(network);

        if (network == null || networkCapabilities == null) return "raw";

        String format;
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            format = Preferences.getAudioTranscodeFormatWifi();
            Log.d(TAG, "DEBUG: Using WIFI Format: " + format);
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            format = Preferences.getAudioTranscodeFormatMobile();
            Log.d(TAG, "DEBUG: Using MOBILE Format: " + format);
        } else {
            format = Preferences.getAudioTranscodeFormatWifi();
        }
        return format;
    }

    public static String getBitratePreferenceForDownload() {
        String audioTranscodeFormat = getTranscodingFormatPreferenceForDownload();

        if (audioTranscodeFormat.equals("raw"))
            return "0";

        return Preferences.getBitrateTranscodedDownload();
    }

    public static String getTranscodingFormatPreferenceForDownload() {
        return Preferences.getAudioTranscodeFormatTranscodedDownload();
    }

    public static List<Child> limitPlayableMedia(List<Child> toLimit, int position) {
        if (!toLimit.isEmpty() && toLimit.size() > Constants.PLAYABLE_MEDIA_LIMIT) {
            int from = position < Constants.PRE_PLAYABLE_MEDIA ? 0 : position - Constants.PRE_PLAYABLE_MEDIA;
            int to = Math.min(from + Constants.PLAYABLE_MEDIA_LIMIT, toLimit.size());

            return toLimit.subList(from, to);
        }

        return toLimit;
    }

    public static int getPlayableMediaPosition(List<Child> toLimit, int position) {
        if (!toLimit.isEmpty() && toLimit.size() > Constants.PLAYABLE_MEDIA_LIMIT) {
            return Math.min(position, Constants.PRE_PLAYABLE_MEDIA);
        }

        return position;
    }

    private static ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static void ratingFilter(List<Child> toFilter) {
        if (toFilter == null || toFilter.isEmpty()) return;

        List<Child> filtered = toFilter
                .stream()
                .filter(child -> (child.getUserRating() != null && child.getUserRating() >= Preferences.getMinStarRatingAccepted()) || (child.getUserRating() == null))
                .collect(Collectors.toList());

        toFilter.clear();

        toFilter.addAll(filtered);
    }

    public static boolean isImageUrl(String url) {
        if (url == null || url.isEmpty())
            return false;
        String path = url.toLowerCase().trim().split("\\?")[0];

        return path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".webp") ||
                path.endsWith(".gif") || path.endsWith(".bmp") ||
                path.endsWith(".svg");
    }
}