/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.*;

/** Changes the native main letter's size, retaining its font, color and draw path. */
final class PrimaryLetterSize {
    static final int DEFAULT_SP = 24, MIN_SP = 12, MAX_SP = 40;
    private static final Map<TextView, Size> overrides = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> internalWrite = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return false; }
    };
    private final Map<TextView, Size> saved = new WeakHashMap<>();
    private static final class Size {
        float original, applied;
        int sp;
        Size(float original) { this.original = this.applied = original; }
    }
    /** Called before native setters: remember the native value and avoid layout thrashing. */
    static Float nativeRequest(TextView label, int unit, float requested) {
        if (internalWrite.get()) return null;
        synchronized (overrides) {
            Size size = overrides.get(label);
            if (size == null) return null;
            size.original = TypedValue.applyDimension(unit, requested, label.getResources().getDisplayMetrics());
            if (!letter(label.getText())) { size.applied = size.original; return null; }
            size.applied = pixels(label, size.sp);
            return (float)size.sp;
        }
    }
    void apply(View key, boolean enabled, int sp) {
        TextView primary = enabled ? primary(key) : null;
        if (primary != null && !letter(primary.getText())) primary = null;
        for (Map.Entry<TextView, Size> entry : new ArrayList<>(saved.entrySet())) {
            TextView label = entry.getKey();
            if (label != primary) {
                restore(label, entry.getValue()); saved.remove(label);
            }
        }
        if (primary == null) return;
        Size size = saved.get(primary);
        if (size == null) { size = new Size(primary.getTextSize()); saved.put(primary, size); }
        else if (Math.abs(primary.getTextSize() - size.applied) > .01f) size.original = primary.getTextSize();
        size.sp = Math.max(MIN_SP, Math.min(MAX_SP, sp));
        size.applied = pixels(primary, size.sp);
        synchronized (overrides) { overrides.put(primary, size); }
        if (Math.abs(primary.getTextSize() - size.applied) > .01f) setPixels(primary, size.applied);
    }
    void restore() { apply(null, false, DEFAULT_SP); }
    private static void restore(TextView label, Size size) {
        if (label == null) return;
        synchronized (overrides) { overrides.remove(label); }
        if (Math.abs(label.getTextSize() - size.applied) <= .01f) setPixels(label, size.original);
    }
    private static float pixels(TextView label, int sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, label.getResources().getDisplayMetrics());
    }
    private static void setPixels(TextView label, float pixels) {
        boolean previous = internalWrite.get(); internalWrite.set(true);
        try { label.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels); }
        finally { internalWrite.set(previous); }
    }
    private TextView primary(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && ((TextView)view).getText().length() > 0) return (TextView)view;
        TextView best = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView candidate = primary(group.getChildAt(i));
                if (candidate != null && (best == null || original(candidate) > original(best))) best = candidate;
            }
        }
        return best;
    }
    private float original(TextView view) {
        Size size = saved.get(view);
        return size == null ? view.getTextSize() : size.original;
    }
    private static boolean letter(CharSequence value) {
        if (value == null || value.length() == 0) return false;
        int first = Character.codePointAt(value, 0), index = Character.charCount(first);
        if (first == ';' && index == value.length()) return true;
        if (!Character.isLetter(first)) return false;
        while (index < value.length()) {
            int code = Character.codePointAt(value, index), type = Character.getType(code);
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) return false;
            index += Character.charCount(code);
        }
        return true;
    }
}
