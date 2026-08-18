package com.cappielloantonio.tempo.radiobrowser;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Keep;

@Keep
public class RadioBrowserCountry {
    public String name;
    @SerializedName("iso_3166_1")
    public String isoCode;
    @SerializedName("stationcount")
    public int stationCount;
}
