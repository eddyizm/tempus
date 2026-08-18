package com.eddyizm.tempus.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class MusicFolder(
    var id: String? = null,
    var name: String? = null,
) : Parcelable