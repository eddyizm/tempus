package com.cappielloantonio.tempo.subsonic.models

import androidx.annotation.Keep

@Keep
class ErrorCode(val value: Int) {
    companion object {
        const val GENERIC_ERROR = 0
        const val REQUIRED_PARAMETER_MISSING = 10
        const val INCOMPATIBLE_VERSION_CLIENT = 20
        const val INCOMPATIBLE_VERSION_SERVER = 30
        const val WRONG_USERNAME_OR_PASSWORD = 40
        const val TOKEN_AUTHENTICATION_NOT_SUPPORTED = 41
        const val USER_NOT_AUTHORIZED = 50
        const val TRIAL_PERIOD_OVER = 60
        const val DATA_NOT_FOUND = 70
    }
}