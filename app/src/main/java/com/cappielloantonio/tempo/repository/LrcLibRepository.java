package com.cappielloantonio.tempo.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.lyrics.lrclib.LrcLibLyricsResult;
import com.cappielloantonio.tempo.lyrics.lrclib.LrcLibResponse;
import com.cappielloantonio.tempo.lyrics.lrclib.LrcLibRetrofitClient;
import com.cappielloantonio.tempo.lyrics.lrclib.LrcLibService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Line;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LrcLibRepository {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");

    private final LrcLibService lrcLibService;

    public LrcLibRepository() {
        lrcLibService = new LrcLibRetrofitClient().getRetrofit().create(LrcLibService.class);
    }

    public MutableLiveData<LrcLibLyricsResult> getSyncedLyrics(Child song) {
        MutableLiveData<LrcLibLyricsResult> liveData = new MutableLiveData<>();

        if (!shouldRequest(song)) {
            liveData.setValue(null);
            return liveData;
        }

        lrcLibService.getLyrics(song.getTitle(), song.getArtist(), song.getAlbum(), song.getDuration())
                .enqueue(new Callback<LrcLibResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LrcLibResponse> call, @NonNull Response<LrcLibResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            liveData.setValue(null);
                            return;
                        }

                        LyricsList lyricsList = mapToLyricsList(song, response.body());
                        String plainLyrics = TextUtils.isEmpty(response.body().getSyncedLyrics())
                                ? response.body().getPlainLyrics()
                                : null;

                        liveData.setValue(new LrcLibLyricsResult(lyricsList, plainLyrics));
                    }

                    @Override
                    public void onFailure(@NonNull Call<LrcLibResponse> call, @NonNull Throwable t) {
                        liveData.setValue(null);
                    }
                });

        return liveData;
    }

    private boolean shouldRequest(Child song) {
        return song != null && !TextUtils.isEmpty(song.getTitle()) && !TextUtils.isEmpty(song.getArtist());
    }

    @Nullable
    private LyricsList mapToLyricsList(@NonNull Child song, @NonNull LrcLibResponse response) {
        List<Line> lines = parseSyncedLyrics(response.getSyncedLyrics());

        if (lines.isEmpty()) {
            return null;
        }

        StructuredLyrics structuredLyrics = new StructuredLyrics();
        structuredLyrics.setDisplayArtist(song.getArtist());
        structuredLyrics.setDisplayTitle(song.getTitle());
        structuredLyrics.setLang(response.getLanguage());
        structuredLyrics.setSynced(true);
        structuredLyrics.setLine(lines);

        LyricsList lyricsList = new LyricsList();
        lyricsList.setStructuredLyrics(Collections.singletonList(structuredLyrics));
        return lyricsList;
    }

    @NonNull
    private List<Line> parseSyncedLyrics(String syncedLyrics) {
        if (TextUtils.isEmpty(syncedLyrics)) {
            return Collections.emptyList();
        }

        List<Line> parsedLines = new ArrayList<>();
        String[] rows = syncedLyrics.split("\\r?\\n");

        for (String row : rows) {
            if (TextUtils.isEmpty(row)) {
                continue;
            }

            Matcher matcher = TIMESTAMP_PATTERN.matcher(row);
            List<Integer> timestamps = new ArrayList<>();
            int lastEnd = 0;

            while (matcher.find()) {
                Integer start = toMillis(matcher.group(1), matcher.group(2), matcher.group(3));
                if (start != null) {
                    timestamps.add(start);
                }
                lastEnd = matcher.end();
            }

            if (timestamps.isEmpty()) {
                continue;
            }

            String text = row.substring(lastEnd).trim();
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            for (Integer timestamp : timestamps) {
                Line line = new Line();
                line.setStart(timestamp);
                line.setValue(text);
                parsedLines.add(line);
            }
        }

        if (parsedLines.isEmpty()) {
            return Collections.emptyList();
        }

        parsedLines.sort(new Comparator<Line>() {
            @Override
            public int compare(Line line1, Line line2) {
                Integer start1 = line1.getStart();
                Integer start2 = line2.getStart();

                if (start1 == null && start2 == null) return 0;
                if (start1 == null) return 1;
                if (start2 == null) return -1;

                return Integer.compare(start1, start2);
            }
        });

        return parsedLines;
    }

    @Nullable
    private Integer toMillis(String minutesString, String secondsString, @Nullable String fractionString) {
        try {
            int minutes = Integer.parseInt(minutesString);
            int seconds = Integer.parseInt(secondsString);
            double fraction = 0;

            if (!TextUtils.isEmpty(fractionString)) {
                fraction = Double.parseDouble("0." + fractionString);
            }

            double totalSeconds = (minutes * 60) + seconds + fraction;
            return (int) Math.round(totalSeconds * 1000);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

