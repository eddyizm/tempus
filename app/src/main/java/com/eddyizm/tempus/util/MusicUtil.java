package com.eddyizm.tempus.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Html;
import android.util.Log;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.repository.DownloadRepository;
import com.eddyizm.tempus.subsonic.models.Child;

import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

        int transport = getActiveTransport();
        String selectedFormat = transcodingFormatFor(transport);
        String selectedBitrate = bitrateFor(transport, selectedFormat);
        Log.i(TAG, "DEBUG: Requesting Format: " + selectedFormat + " at Bitrate: " + selectedBitrate);

        if (!Preferences.isServerPrioritized())
            uri.append("&maxBitRate=").append(selectedBitrate);
        if (!Preferences.isServerPrioritized())
            uri.append("&format=").append(selectedFormat);
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

        int transport = getActiveTransport();
        String format = transcodingFormatFor(transport);

        if (!Preferences.isServerPrioritized())
            s += "&maxBitRate=" + bitrateFor(transport, format);
        if (!Preferences.isServerPrioritized())
            s += "&format=" + format;

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
        if (!Preferences.showAudioQuality()) return "";

        // A transcode with no bitrate ceiling has a null bitrate, which used to blank the badge.
        boolean hasBitrate = child.getBitrate() != null;
        boolean hasSuffix = child.getSuffix() != null && !child.getSuffix().isEmpty();
        if (!hasBitrate && !hasSuffix) return "";

        String detail = child.getBitDepth() != null && child.getBitDepth() != 0
                ? child.getBitDepth() + "/" + (child.getSamplingRate() != null ? child.getSamplingRate() / 1000 : "")
                : (child.getSamplingRate() != null
                ? new DecimalFormat("0.#").format(child.getSamplingRate() / 1000.0) + "kHz"
                : "");

        if (hasSuffix) {
            detail = detail.isEmpty() ? child.getSuffix() : detail + " " + child.getSuffix();
        }

        return "•" +
                " " +
                (hasBitrate ? child.getBitrate() + "kbps" : "") +
                (hasBitrate && !detail.isEmpty() ? " • " : "") +
                detail;
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

    // One second memo of the active network transport so the per song bitrate and format
    // lookups stop firing a getActiveNetwork binder call for every song.
    private static final int TRANSPORT_NONE = -1;
    private static final int TRANSPORT_OTHER = -2;
    private static final long NETWORK_CACHE_TTL_MS = 1000;

    // Held as one immutable value rather than a transport field and a timestamp field, so a
    // reader cannot pick up a transport paired with someone else's timestamp.
    private static final class TransportMemo {
        final int transport;
        final long at;

        TransportMemo(int transport, long at) {
            this.transport = transport;
            this.at = at;
        }
    }

    private static final AtomicReference<TransportMemo> transportMemo = new AtomicReference<>();

    // Ethernet, bluetooth and USB tethering land in TRANSPORT_OTHER and stay there. A VPN lands
    // there only in passing, while the network underneath it hands over.
    public static int transportOf(NetworkCapabilities caps) {
        if (caps == null) return TRANSPORT_NONE;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return NetworkCapabilities.TRANSPORT_WIFI;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return NetworkCapabilities.TRANSPORT_CELLULAR;
        return TRANSPORT_OTHER;
    }

    // The transport came from the framework with the change, so it replaces whatever is stored:
    // a lookup that began before the callback and finished after it would write back a stale one.
    public static void primeActiveTransport(int transport) {
        transportMemo.set(new TransportMemo(transport, SystemClock.elapsedRealtime()));
    }

    public static int getActiveTransport() {
        long now = SystemClock.elapsedRealtime();
        TransportMemo memo = transportMemo.get();
        if (memo != null && now - memo.at < NETWORK_CACHE_TTL_MS) return memo.transport;

        Network network = getConnectivityManager().getActiveNetwork();
        int transport = transportOf(network == null ? null : getConnectivityManager().getNetworkCapabilities(network));

        // A value stored while the lookup was running came either from a network callback or from
        // a lookup of its own. Keep it and answer with it rather than overwriting it.
        if (!transportMemo.compareAndSet(memo, new TransportMemo(transport, now))) {
            return transportMemo.get().transport;
        }
        return transport;
    }

    // The format and the ceiling have to describe the same network, so both resolve from a single
    // transport read. Two reads let a handover land between them and pair settings never configured.
    private static String transcodingFormatFor(int transport) {
        if (transport == TRANSPORT_NONE) return "raw";
        if (transport == NetworkCapabilities.TRANSPORT_CELLULAR)
            return Preferences.getAudioTranscodeFormatMobile();
        return Preferences.getAudioTranscodeFormatWifi();
    }

    private static String bitrateFor(int transport, String format) {
        // No ceiling applies to an untranscoded stream. This also covers TRANSPORT_NONE, which
        // transcodingFormatFor already answers with "raw".
        if (format.equals("raw")) return "0";
        if (transport == NetworkCapabilities.TRANSPORT_CELLULAR)
            return Preferences.getMaxBitrateMobile();
        return Preferences.getMaxBitrateWifi();
    }

    public static String getBitratePreference() {
        int transport = getActiveTransport();
        return bitrateFor(transport, transcodingFormatFor(transport));
    }

    public static String getTranscodingFormatPreference() {
        return transcodingFormatFor(getActiveTransport());
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

    // A client transcode leaves the row's inherited Child metadata describing the source rather
    // than the file on disk. Call on any Download row before it is inserted. See issue 502.
    public static void applyTranscodedDownloadMetadata(Download download) {
        if (download == null) return;
        if (!Preferences.preferTranscodedDownload() || Preferences.isServerPrioritizedInTranscodedDownload())
            return;

        applyTranscodeMetadata(download, getTranscodingFormatPreferenceForDownload(),
                getBitratePreferenceForDownload());
    }

    // The same rewrite, for a caller that knows the format and ceiling a file was actually fetched
    // with rather than the ones configured now.
    public static void applyTranscodeMetadata(Download download, String format, String maxBitRate) {
        if (download == null) return;
        if (format == null || format.equals("raw")) return;

        download.setSuffix(format);
        // Moves with the suffix, since the track info dialog reads it.
        String mime = audioMimeTypeForFormat(format);
        if (mime != null) download.setContentType(mime);
        download.setSamplingRate(null);
        download.setBitDepth(null);

        // maxBitRate is the configured ceiling; "0" means no limit, so the real bitrate is unknown.
        Integer bitrate = null;
        try {
            int parsed = Integer.parseInt(maxBitRate);
            if (parsed > 0) bitrate = parsed;
        } catch (NumberFormatException ignored) {
        }
        download.setBitrate(bitrate);
    }

    // Inverse of audioFormatLabel. Null for an unrecognized format, so callers keep what they have.
    public static String audioMimeTypeForFormat(String format) {
        if (format == null) return null;
        switch (format.toLowerCase()) {
            case "opus": return "audio/opus";
            case "aac": return "audio/aac";
            case "mp3": return "audio/mpeg";
            case "flac": return "audio/flac";
            default: return null;
        }
    }

    // Maps a Media3 sample MIME type (e.g. "audio/flac") to a short, user-facing format
    // label (e.g. "flac"). Returns null when the mime is null. Used to show the format the
    // player is actually decoding rather than the requested transcode preference. See #579.
    public static String audioFormatLabel(String sampleMimeType) {
        if (sampleMimeType == null) return null;
        String mime = sampleMimeType.toLowerCase();
        if (mime.contains("flac")) return "flac";
        if (mime.contains("opus")) return "opus";
        if (mime.contains("vorbis")) return "vorbis";
        if (mime.contains("mp4a") || mime.contains("aac")) return "aac";
        if (mime.contains("mpeg") || mime.contains("mp3")) return "mp3";
        if (mime.contains("alac")) return "alac";
        if (mime.contains("eac3")) return "eac3";
        if (mime.contains("ac3")) return "ac3";
        if (mime.contains("pcm") || mime.contains("raw") || mime.contains("wav")) return "wav";
        if (mime.contains("ogg")) return "ogg";
        int slash = mime.indexOf('/');
        return slash >= 0 && slash < mime.length() - 1 ? mime.substring(slash + 1) : mime;
    }

    // The player reports the decoded codec (aac, vorbis, opus), but the source "suffix" is often a
    // container name (m4a, ogg, oga) that legitimately holds that codec. Treating a container/codec
    // pair as a mismatch would tag direct-played files as "(transcoding)". Only report a transcode
    // when the decoded codec is not one the source container can carry. See issue 579 and 669.
    public static boolean isTranscodedFormat(String decodedLabel, String sourceSuffix) {
        if (decodedLabel == null || decodedLabel.isEmpty()) return false;
        if (sourceSuffix == null || sourceSuffix.isEmpty()) return false;
        if (decodedLabel.equalsIgnoreCase(sourceSuffix)) return false;
        switch (sourceSuffix.toLowerCase()) {
            case "m4a":
            case "m4b":
            case "mp4":
                return !decodedLabel.equals("aac") && !decodedLabel.equals("alac");
            case "ogg":
            case "oga":
                return !decodedLabel.equals("vorbis") && !decodedLabel.equals("opus")
                        && !decodedLabel.equals("flac");
            default:
                return true;
        }
    }

    // True when the current item is played from the device (a content or file uri scheme)
    // rather than streamed and transcoded by the server. Returns false when the browser is
    // null or the current item has no resolvable uri. See issue 579.
    @UnstableApi
    public static boolean isCurrentTrackLocal(MediaBrowser browser) {
        if (browser == null || browser.getCurrentMediaItem() == null) return false;
        Uri currentUri = browser.getCurrentMediaItem().requestMetadata.mediaUri;
        if (currentUri == null) return false;
        String scheme = currentUri.getScheme();
        return "content".equals(scheme) || "file".equals(scheme);
    }

    // The audio Format the player is currently decoding, so callers can show what is really
    // playing instead of the requested transcode preference. Prefers the selected audio track;
    // on the first load the controller can report tracks before one is marked selected, so it
    // falls back to the first audio track and returns null only when no audio track is
    // available yet. See issue 579.
    @UnstableApi
    public static Format getCurrentAudioFormat(MediaBrowser browser) {
        if (browser == null) return null;
        Format firstAudio = null;
        for (Tracks.Group group : browser.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i);
                if (firstAudio == null) firstAudio = group.getTrackFormat(i);
            }
        }
        return firstAudio;
    }

    // The original source suffix (for example "flac" or "m4a") stored on the current item's
    // metadata, used to decide whether the decoded format differs from the source. Returns
    // null when the browser is null or no suffix is present. See issue 579.
    @UnstableApi
    public static String getCurrentOriginalSuffix(MediaBrowser browser) {
        if (browser == null || browser.getMediaMetadata().extras == null) return null;
        return browser.getMediaMetadata().extras.getString("suffix", null);
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