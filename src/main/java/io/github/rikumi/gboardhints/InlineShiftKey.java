/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Reversible shift icon; the key's native decode action is intercepted separately. */
final class InlineShiftKey {
    private final Map<View, Float> hidden = new WeakHashMap<>();
    private final ShiftIcon icon = new ShiftIcon();
    private WeakReference<View> owner = new WeakReference<>(null);
    private CharSequence description;
    private Object definition;
    void apply(View key, Object metadata, boolean active, boolean armed) {
        if (!active || definition != metadata || owner.get() != key) restore();
        if (!active) return;
        if (owner.get() == null) {
            owner = new WeakReference<>(key); definition = metadata; description = key.getContentDescription();
            key.getOverlay().add(icon);
        }
        hide(key);
        key.setContentDescription(armed ? "行内大写，下一字母大写" : "行内大写");
        TypedArray colors = key.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color;
        try { color = colors.getColor(0, Color.GRAY); } finally { colors.recycle(); }
        for (View label : hidden.keySet()) if (label instanceof TextView && ((TextView)label).getText().length() > 0) {
            color = ((TextView)label).getCurrentTextColor(); break;
        }
        icon.update(key, color, armed);
    }
    void restore() {
        View key = owner.get();
        if (key != null) { key.getOverlay().remove(icon); key.setContentDescription(description); }
        for (Map.Entry<View, Float> entry : hidden.entrySet()) if (entry.getKey() != null) entry.getKey().setAlpha(entry.getValue());
        hidden.clear(); owner.clear(); definition = null;
    }
    private void hide(View view) {
        if (view instanceof TextView || view instanceof ImageView) {
            if (!hidden.containsKey(view)) hidden.put(view, view.getAlpha());
            view.setAlpha(0f);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) hide(group.getChildAt(i));
        }
    }
    private static final class ShiftIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        void update(View key, int color, boolean armed) {
            RectF face = AdaptiveKeyAdapter.faceBounds(key);
            float side = Math.min(24 * key.getResources().getDisplayMetrics().density, Math.min(face.width(), face.height()) * .65f);
            float x = face.centerX() - side / 2, y = face.centerY() - side / 2;
            path.reset(); path.moveTo(x + side / 2, y); path.lineTo(x + side, y + side / 2);
            path.lineTo(x + side * .75f, y + side / 2); path.lineTo(x + side * .75f, y + side);
            path.lineTo(x + side * .25f, y + side); path.lineTo(x + side * .25f, y + side / 2);
            path.lineTo(x, y + side / 2); path.close();
            paint.setColor(color); paint.setStyle(armed ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * key.getResources().getDisplayMetrics().density); paint.setStrokeJoin(Paint.Join.ROUND);
            setBounds(0, 0, key.getWidth(), key.getHeight()); invalidateSelf();
        }
        @Override public void draw(Canvas canvas) { canvas.drawPath(path, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
