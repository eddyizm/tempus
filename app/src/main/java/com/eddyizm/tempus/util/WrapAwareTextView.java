package com.eddyizm.tempus.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * A single-line, scrolling label that can be switched to a wrapped, two-line label.
 *
 * Wrapping is decided when the view is inflated, so the preference takes effect the next time a
 * screen opens rather than updating labels that are already on screen.
 */
public class WrapAwareTextView extends AppCompatTextView {

    private static final int MAX_WRAPPED_LINES = 2;

    public WrapAwareTextView(Context context) {
        this(context, null);
    }

    public WrapAwareTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WrapAwareTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyWrappingPreference();
    }

    private void applyWrappingPreference() {
        if (!Preferences.getWrapLongText()) return;

        setSingleLine(false);
        setMaxLines(MAX_WRAPPED_LINES);
        setEllipsize(TextUtils.TruncateAt.END);
        setHorizontallyScrolling(false);
        if (isSelected()) setSelected(false);
    }
}
