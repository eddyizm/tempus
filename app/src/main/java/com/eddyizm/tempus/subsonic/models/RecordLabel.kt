package com.eddyizm.tempus.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
open class RecordLabel(
    var name: String? = null,
) : Parcelable