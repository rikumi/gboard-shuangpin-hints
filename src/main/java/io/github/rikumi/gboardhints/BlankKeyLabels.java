/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.view.View;
import android.view.Gravity;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.*;

/** Changes rendering alpha only: key actions, layout and accessibility labels stay intact. */
final class BlankKeyLabels {
    private final Map<TextView, Float> hidden = new WeakHashMap<>();
    void apply(View key, boolean enabled, char primaryKey) {
        apply(key, enabled, primaryKey, false);
    }
    void apply(View key, boolean enabled, char primaryKey, boolean primaryOnly) {
        apply(key, enabled, primaryKey, primaryOnly, false);
    }
    void apply(View key, boolean enabled, char primaryKey, boolean primaryOnly, boolean keepPrimary) {
        apply(key, enabled, primaryKey, primaryOnly, keepPrimary, false);
    }
    void apply(View key, boolean enabled, char primaryKey, boolean primaryOnly, boolean keepPrimary, boolean hideNumbers) {
        Set<TextView> desired = Collections.newSetFromMap(new IdentityHashMap<>());
        if (enabled && (primaryKey >= 'a' && primaryKey <= 'z' || primaryKey == ';')) {
            if (primaryOnly) {
                TextView primary = AdaptiveKeyAdapter.textLabel(key);
                if (primary != null) desired.add(primary);
            } else collect(key, desired);
        }
        if (keepPrimary && key != null) desired.remove(AdaptiveKeyAdapter.textLabel(key));
        if (hideNumbers && key != null) collectNumberHints(key, desired);
        for (Map.Entry<TextView, Float> entry : new ArrayList<>(hidden.entrySet())) {
            TextView view = entry.getKey();
            if (view != null && !desired.contains(view)) { view.setAlpha(entry.getValue()); hidden.remove(view); }
        }
        for (TextView view : desired) {
            if (!hidden.containsKey(view)) hidden.put(view, view.getAlpha());
            view.setAlpha(0f);
        }
    }
    void restore() { apply(null, false, (char)0); }
    private static void collectNumberHints(View key, Set<TextView> out) {
        Set<TextView> labels = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(key, labels);
        TextView primary = null;
        for (TextView label : labels) {
            if (label.getVisibility() == View.VISIBLE && label.getText().length() > 0
                    && (primary == null || label.getTextSize() > primary.getTextSize())) primary = label;
        }
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (label == primary || label.getVisibility() != View.VISIBLE || text.length() != 1
                    || !Character.isDigit(text.charAt(0))) continue;
            RectF bounds = AdaptiveKeyAdapter.inKey(label, key,
                    new RectF(0, 0, label.getWidth(), label.getHeight()));
            int gravity = Gravity.getAbsoluteGravity(label.getGravity(), label.getLayoutDirection());
            boolean right = bounds.centerX() > key.getWidth() * .55f
                    || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
            boolean top = bounds.centerY() < key.getHeight() * .5f
                    || (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP;
            if (right && top) out.add(label);
        }
    }
    private static void collect(View view, Set<TextView> out) {
        if (view instanceof TextView) out.add((TextView)view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
        }
    }
}
