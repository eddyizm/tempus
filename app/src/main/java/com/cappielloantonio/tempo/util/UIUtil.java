package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;

import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;

import org.jspecify.annotations.NonNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UIUtil {
    public static int getSpanCount(int itemCount, int maxSpan) {
        int itemSize = itemCount == 0 ? 1 : itemCount;

        if (itemSize / maxSpan > 0) {
            return maxSpan;
        } else {
            return itemSize % maxSpan;
        }
    }

    public static DividerItemDecoration getDividerItemDecoration(Context context) {
        int[] ATTRS = new int[]{android.R.attr.listDivider};

        TypedArray a = context.obtainStyledAttributes(ATTRS);
        Drawable divider = a.getDrawable(0);
        InsetDrawable insetDivider = new InsetDrawable(divider, 42, 0, 42, 42);
        a.recycle();

        DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(insetDivider);

        return itemDecoration;
    }

    private static LocaleListCompat getLocalesFromResources(Context context) {
        final List<String> tagsList = new ArrayList<>();

        XmlPullParser xpp = context.getResources().getXml(R.xml.locale_config);

        try {
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                String tagName = xpp.getName();

                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    if ("locale".equals(tagName) && xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("name")) {
                        tagsList.add(xpp.getAttributeValue(0));
                    }
                }

                xpp.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }

        return LocaleListCompat.forLanguageTags(String.join(",", tagsList));
    }

    public static Map<String, String> getLangPreferenceDropdownEntries(Context context) {
        LocaleListCompat localeList = getLocalesFromResources(context);

        List<Map.Entry<String, String>> localeArrayList = new ArrayList<>();

        String systemDefaultLabel = App.getContext().getString(R.string.settings_system_language);
        String systemDefaultValue = "default";

        for (int i = 0; i < localeList.size(); i++) {
            Locale locale = localeList.get(i);
            if (locale != null) {
                localeArrayList.add(
                        new AbstractMap.SimpleEntry<>(
                                Util.toPascalCase(locale.getDisplayName()),
                                locale.toLanguageTag()
                        )
                );
            }
        }

        localeArrayList.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));

        LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
        orderedMap.put(systemDefaultLabel, systemDefaultValue);
        for (Map.Entry<String, String> entry : localeArrayList) {
            orderedMap.put(entry.getKey(), entry.getValue());
        }

        return orderedMap;
    }

    public static String getReadableDate(Date date) {
        if (date == null) {
            return App.getContext().getString(R.string.share_no_expiration); 
        }
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
        return formatter.format(date);
    }

    public static RecyclerView.ItemDecoration horizontalSpacing(int spacingPx) {
        return new HorizontalSpacingItemDecoration(spacingPx);
    }

    public static RecyclerView.OnScrollListener scaleOnScroll() {
        return new ScaleOnScrollListener();
    }

    private static class HorizontalSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacingPx;

        HorizontalSpacingItemDecoration(int spacingPx) {
            this.spacingPx = spacingPx;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   RecyclerView.@NonNull State state) {
            int pos = parent.getChildAdapterPosition(view);
            outRect.left  = (pos == 0) ? spacingPx : 0;
            outRect.right = spacingPx;
        }
    }

    private static class ScaleOnScrollListener extends RecyclerView.OnScrollListener {
        private static final float MAX = 1.0f;
        private static final float MIN = 0.70f;
        private static final float ELEVATION_FACTOR = 8f;

        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            final float centerX = rv.getWidth() / 2f;

            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);

                float childCenter = (child.getLeft() + child.getRight()) / 2f;
                float distance    = Math.abs(centerX - childCenter);
                float scale       = MAX - (distance / centerX) * (MAX - MIN);

                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setElevation(scale * ELEVATION_FACTOR);
            }
        }
    }

    public static void centerAndSnapRecyclerView(@NonNull RecyclerView rv) {
        final PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView,
                                             int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;

                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;

                View snapView = snapHelper.findSnapView(lm);
                if (snapView == null) return;

                int[] distance = snapHelper.calculateDistanceToFinalSnap(lm, snapView);
                if (distance == null) return;

                if (distance[0] != 0 || distance[1] != 0) {
                    recyclerView.smoothScrollBy(distance[0], distance[1]);
                }
            }
        });
    }

}
