/* SPDX-License-Identifier: LGPL-2.1-or-later */
package com.google.android.libraries.inputmethod.widgets;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

/** JVM fixture matching the stable native owner name and registration shape. */
public class SoftKeyboardView extends FrameLayout {
    public final SparseArray<View> registered = new SparseArray<>();
    public SoftKeyboardView(Context context) { super(context); }
    private void register(View key) { registered.put(key.getId(), key); }
}
