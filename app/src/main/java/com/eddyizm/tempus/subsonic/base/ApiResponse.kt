package com.eddyizm.tempus.subsonic.base

import androidx.annotation.Keep
import com.eddyizm.tempus.subsonic.models.ResponseStatus
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.google.gson.annotations.SerializedName

@Keep
class ApiResponse {
    @SerializedName("subsonic-response")
    var subsonicResponse: SubsonicResponse = SubsonicResponse().apply {
        status = ResponseStatus.FAILED
    }
}