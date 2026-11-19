package com.eddyizm.tempus.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class ScanStatus {
    @SerializedName("scanning")
    var isScanning = false
    var count: Long? = null
}
