package com.cappielloantonio.tempo.radiobrowser;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Keep;

@Keep
public class RadioBrowserStation {
    @SerializedName("stationuuid")
    public String stationUuid;
    public String name;
    public String url;
    @SerializedName("url_resolved")
    public String urlResolved;
    public String homepage;
    public String favicon;
    public String tags;
    public String country;
    @SerializedName("countrycode")
    public String countryCode;
    public String language;
    public String codec;
    public int bitrate;
    @SerializedName("clickcount")
    public int clickCount;
    @SerializedName("votes")
    public int votes;
}
