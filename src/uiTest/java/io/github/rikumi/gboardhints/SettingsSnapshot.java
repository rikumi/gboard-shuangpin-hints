/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.graphics.Bitmap;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.graphics.AndroidImageBitmap_androidKt;
import androidx.compose.ui.test.AndroidImageHelpers_androidKt;
import androidx.compose.ui.test.SemanticsNodeInteraction;

/** Calls newer Compose screenshot APIs without inlining them into Java 8 test bytecode. */
public final class SettingsSnapshot {
    public static float[] bounds(SemanticsNodeInteraction node) {
        Rect rect = node.fetchSemanticsNode("Settings element").getBoundsInRoot();
        return new float[]{rect.getLeft(), rect.getTop(), rect.getWidth(), rect.getHeight()};
    }
    public static android.widget.EditText editor(android.view.View view) {
        if (view instanceof android.widget.EditText) return (android.widget.EditText)view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.EditText found = editor(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    public static Bitmap capture(SemanticsNodeInteraction node) {
        return AndroidImageBitmap_androidKt.asAndroidBitmap(AndroidImageHelpers_androidKt.captureToImage(node))
                .copy(Bitmap.Config.ARGB_8888, false);
    }
}
