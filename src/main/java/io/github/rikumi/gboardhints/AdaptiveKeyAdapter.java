/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.*;

/** Uses visible labels, with metadata arrays as a fallback for custom-rendered keys. */
final class AdaptiveKeyAdapter {
    static final String KEY_CLASS = "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    static Field metadataField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())
                && field.getType().getName().endsWith(".metadata.SoftKeyDef")) {
            field.setAccessible(true); return field;
        }

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            for (Field member : field.getType().getDeclaredFields()) {
                Class<?> t = member.getType();
                if (t.isArray() && CharSequence.class.isAssignableFrom(t.getComponentType())) {
                    field.setAccessible(true); return field;
                }
            }
        }
        // Newer Gboard versions keep stable metadata names and List<String> labels.
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().getName().endsWith(".metadata.SoftKeyDef")) {
                field.setAccessible(true); return field;
            }
        }
        return null;
    }
    static TextView textLabel(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && letter(((TextView)view).getText()) != 0) return (TextView)view;
        TextView best = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView candidate = textLabel(group.getChildAt(i));
                if (candidate != null && (best == null || candidate.getTextSize() > best.getTextSize())) best = candidate;
            }
        }
        return best;
    }
    static char letter(CharSequence value) {
        if (value == null || value.length() != 1) return 0;
        char c = Character.toLowerCase(value.charAt(0));
        return c >= 'a' && c <= 'z' || c == ';' ? c : 0;
    }
    static char key(View view, Field metadata) {
        if (metadata != null) {
            try {
                Object def = metadata.get(view);
                if (def == null) return 0;
                if (def != null) for (Field field : def.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    Class<?> t = field.getType();
                    if (t.isArray() && CharSequence.class.isAssignableFrom(t.getComponentType())) {
                        field.setAccessible(true);
                        CharSequence[] labels = (CharSequence[])field.get(def);
                        // Only the primary label is eligible; a secondary 'a' on a symbol is not.
                        if (labels != null && labels.length > 0) return letter(labels[0]);
                    }
                    if (java.util.List.class.isAssignableFrom(t)) {
                        field.setAccessible(true);
                        Object labels = field.get(def);
                        if (labels instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>)labels;
                            if (!list.isEmpty() && list.get(0) instanceof CharSequence)
                                return letter((CharSequence)list.get(0));
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        TextView label = textLabel(view);
        if (label != null) return letter(label.getText());
        return letter(view.getContentDescription());
    }
    /** Visible key faces can be substantially smaller than their touch-target frames. */
    static RectF faceBounds(View key) {
        RectF result = new RectF(0, 0, key.getWidth(), key.getHeight());
        TextView primary = textLabel(key);
        View content = key;
        if (primary != null && primary.getParent() instanceof View) content = (View)primary.getParent();
        else if (key instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)key;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0) {
                    content = child; break;
                }
            }
        }
        RectF local = new RectF(0, 0, content.getWidth(), content.getHeight());
        if (!intersect(result, inKey(content, key, local))) return result;
        for (View current = content; current != null;) {
            Rect backgroundPadding = new Rect();
            if (current.getBackground() != null) current.getBackground().getPadding(backgroundPadding);
            local.set(Math.max(current.getPaddingLeft(), backgroundPadding.left),
                    Math.max(current.getPaddingTop(), backgroundPadding.top),
                    current.getWidth() - Math.max(current.getPaddingRight(), backgroundPadding.right),
                    current.getHeight() - Math.max(current.getPaddingBottom(), backgroundPadding.bottom));
            if (!intersect(result, inKey(current, key, local))) return result;
            if (current == key) break;
            current = current.getParent() instanceof View ? (View)current.getParent() : null;
        }
        // Some Gboard themes put the touch-target gap in the label's own padding.
        if (primary != null && primary.getWidth() >= content.getWidth() * .8f
                && primary.getHeight() >= content.getHeight() * .8f) {
            local.set(primary.getPaddingLeft(), primary.getPaddingTop(),
                    primary.getWidth() - primary.getPaddingRight(), primary.getHeight() - primary.getPaddingBottom());
            if (!intersect(result, inKey(primary, key, local))) return result;
        }
        // Borderless themes may have no separate face or drawable insets at all.
        // Anchor those hints around the actual center glyph, not a tall hit frame.
        if (primary != null && result.height() >= key.getHeight() * .85f && primary.getBaseline() >= 0) {
            Paint.FontMetrics font = primary.getPaint().getFontMetrics();
            float center = primary.getBaseline() + (font.ascent + font.descent) / 2;
            local.set(0, center - primary.getTextSize(), primary.getWidth(), center + primary.getTextSize());
            if (!intersect(result, inKey(primary, key, local))) return result;
        }
        float inset = 2 * key.getResources().getDisplayMetrics().density;
        result.inset(inset, inset);
        if (result.width() <= 0 || result.height() <= 0) result.setEmpty();
        return result;
    }
    private static boolean intersect(RectF result, RectF other) {
        if (other.isEmpty() || !result.intersect(other)) { result.setEmpty(); return false; }
        return true;
    }
    static RectF inKey(View child, View key, RectF bounds) {
        RectF result = new RectF(bounds);
        for (View current = child; current != key;) {
            if (!(current.getParent() instanceof View)) return new RectF();
            View parent = (View)current.getParent();
            current.getMatrix().mapRect(result);
            result.offset(current.getLeft() - parent.getScrollX(), current.getTop() - parent.getScrollY());
            current = parent;
        }
        return result;
    }
    static final class HintDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF face = new RectF();
        private HintEngine.KeyHint hint = HintEngine.KeyHint.EMPTY;
        private float size;
        private float topSize, bottomSize;
        private float topScaleX, bottomScaleX;
        void update(View key, HintEngine.KeyHint label, int sp) {
            hint = label;
            size = sp * key.getResources().getDisplayMetrics().scaledDensity;
            paint.setTypeface(Typeface.DEFAULT);
            TextView primary = textLabel(key);
            if (primary != null) paint.setColor(primary.getCurrentTextColor());
            else {
                TypedArray colors = key.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
                try { ColorStateList c = colors.getColorStateList(0); paint.setColor(c == null ? Color.GRAY : c.getDefaultColor()); }
                finally { colors.recycle(); }
            }
            paint.setAlpha(190);
            face.set(faceBounds(key));
            setBounds(0, 0, key.getWidth(), key.getHeight());
            invalidateSelf();
        }
        @Override public void draw(Canvas canvas) {
            topSize = bottomSize = 0;
            topScaleX = bottomScaleX = 1;
            if (hint.isEmpty() || getBounds().isEmpty() || face.isEmpty()) return;
            topSize = drawRow(canvas, hint.top, face, -1);
            topScaleX = paint.getTextScaleX();
            bottomSize = drawRow(canvas, hint.bottom, face, 1);
            bottomScaleX = paint.getTextScaleX();
        }
        private float drawRow(Canvas canvas, String text, RectF row, int position) {
            paint.setTextScaleX(1f);
            if (text.isEmpty()) return 0;
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size);
            float width = paint.measureText(text);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float height = fm.bottom - fm.top;
            if (width <= 0 || height <= 0) return 0;
            paint.setTextScaleX(Math.min(1f, row.width() / width));
            float baseline = position < 0 ? row.top - fm.top : row.bottom - fm.bottom;
            int saved = canvas.save();
            canvas.clipRect(row);
            canvas.drawText(text, row.centerX(), baseline, paint);
            canvas.restoreToCount(saved);
            return paint.getTextSize();
        }
        float fittedTopTextSize() { return topSize; }
        float fittedBottomTextSize() { return bottomSize; }
        float fittedTopScaleX() { return topScaleX; }
        float fittedBottomScaleX() { return bottomScaleX; }
        Typeface hintTypeface() { return paint.getTypeface(); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
