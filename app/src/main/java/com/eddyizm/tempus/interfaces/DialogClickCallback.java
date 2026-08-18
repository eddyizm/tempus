package com.eddyizm.tempus.interfaces;


import androidx.annotation.Keep;

@Keep
public interface DialogClickCallback {
    default void onPositiveClick() {}

    default void onNegativeClick() {}

    default void onNeutralClick() {}
}
