package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

@Keep
@Parcelize
class InternetRadioStation(
    var id: String? = null,
    var name: String? = null,
    var streamUrl: String? = null,
    @SerializedName("homePageUrl", alternate = ["homepageUrl"])
    var homePageUrl: String? = null,
) : Parcelable