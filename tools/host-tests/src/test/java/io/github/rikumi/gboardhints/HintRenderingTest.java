/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class HintRenderingTest {
    private FrameLayout key, face;
    private TextView primary;
    @Before public void setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        key = new FrameLayout(activity); face = new FrameLayout(activity);
        primary = new TextView(activity); primary.setText("V"); primary.setTextColor(Color.BLACK);
        primary.setGravity(android.view.Gravity.CENTER);
        primary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 24);
        face.addView(primary, new FrameLayout.LayoutParams(-1, -1));
        key.addView(face, new FrameLayout.LayoutParams(48, 46));
        activity.setContentView(key);
        key.layout(0, 0, 60, 100); face.layout(6, 8, 54, 54); placePrimary(48, 46);
    }
    private void placePrimary(int width, int height) {
        primary.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
        primary.layout(0, 0, width, height);
    }
    private Bitmap render(HintEngine.KeyHint hint, int size) {
        Bitmap bitmap = Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888);
        AdaptiveKeyAdapter.HintDrawable drawable = new AdaptiveKeyAdapter.HintDrawable();
        drawable.update(key, hint, size); drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
    private Rect inkBounds(Bitmap bitmap) {
        Rect bounds = new Rect(); boolean found = false;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue;
            if (!found) { bounds.set(x, y, x + 1, y + 1); found = true; }
            else bounds.union(x, y, x + 1, y + 1);
        }
        assertTrue("The hint must remain visible", found);
        return bounds;
    }
    @Test public void hintStaysInsideFaceInsteadOfTouchTargetBottom() {
        Rect ink = inkBounds(render(HintEngine.hint("XIAOHE", 'v', ""), 17));
        assertTrue(ink.left >= 6); assertTrue(ink.right <= 54);
        assertTrue(ink.top >= 8); assertTrue(ink.bottom <= 54);
    }
    @Test public void initialAndFinalOccupySeparateTopAndBottomRows() {
        Bitmap bitmap = render(new HintEngine.KeyHint("zh", "ui"), 10);
        boolean top = false, bottom = false;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue;
            if (y < 24) top = true;
            if (y > 38) bottom = true;
            assertFalse("Center legend needs a free middle row", y >= 26 && y <= 36);
        }
        assertTrue(top); assertTrue(bottom);
    }
    @Test public void longFinalShrinksIndependentlyOfShortInitial() {
        AdaptiveKeyAdapter.HintDrawable drawable = new AdaptiveKeyAdapter.HintDrawable();
        drawable.update(key, new HintEngine.KeyHint("zh", "iang/uang/iong"), 17);
        Bitmap bitmap = Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        assertEquals(drawable.fittedTopTextSize(), drawable.fittedBottomTextSize(), .001f);
        assertEquals(1f, drawable.fittedTopScaleX(), .001f);
        assertTrue(drawable.fittedBottomScaleX() < drawable.fittedTopScaleX());
        Rect ink = inkBounds(bitmap); assertTrue(ink.left >= 6 && ink.right <= 54 && ink.bottom <= 54);
    }
    @Test public void translatedAndScaledFaceKeepsHintsWithinItsBounds() {
        face.setPivotX(0); face.setPivotY(0); face.setScaleX(.8f); face.setScaleY(.8f); face.setTranslationY(-3);
        Rect ink = inkBounds(render(new HintEngine.KeyHint("sh", "iang/uang"), 17));
        assertTrue(ink.left >= 6); assertTrue(ink.right <= 45);
        assertTrue(ink.top >= 5); assertTrue(ink.bottom <= 42);
    }
    @Test public void backgroundInsetsExcludeTheTouchTargetGap() {
        face.setBackground(new InsetDrawable(new ColorDrawable(Color.WHITE), 2, 3, 2, 12));
        Rect ink = inkBounds(render(new HintEngine.KeyHint("", "iong/üe"), 17));
        assertTrue(ink.bottom <= 42);
    }
    @Test public void labelPaddingExcludesExtraSpaceBelowTheKey() {
        face.removeView(primary); key.removeAllViews(); key.addView(primary);
        primary.setPadding(0, 0, 0, 46); placePrimary(60, 100);
        Rect ink = inkBounds(render(new HintEngine.KeyHint("zh", "ui"), 17));
        assertTrue(ink.bottom <= 54);
    }
    @Test public void borderlessTallTouchTargetUsesTheCenterLegendAsAnchor() {
        face.removeView(primary); key.removeAllViews(); key.addView(primary);
        placePrimary(60, 100);
        Rect ink = inkBounds(render(new HintEngine.KeyHint("zh", "ui"), 17));
        assertTrue(ink.toString(), ink.top >= 20); assertTrue(ink.toString(), ink.bottom < 80);
    }
    @Test public void configuredSizeChangesHeightWhileEachKeyFitsItsOwnWidth() {
        AdaptiveKeyAdapter.HintDrawable drawable = new AdaptiveKeyAdapter.HintDrawable();
        HintEngine.KeyHint text = new HintEngine.KeyHint("", "mmmm");
        Bitmap small = Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888);
        drawable.update(key, text, 7); drawable.draw(new Canvas(small));
        float density = key.getResources().getDisplayMetrics().scaledDensity;
        assertEquals(7 * density, drawable.fittedBottomTextSize(), .001f);
        Bitmap large = Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888);
        drawable.update(key, text, 17); drawable.draw(new Canvas(large));
        assertEquals(17 * density, drawable.fittedBottomTextSize(), .001f);
        assertTrue(inkBounds(large).height() > inkBounds(small).height());
        float wideScale = drawable.fittedBottomScaleX();
        face.layout(6, 8, 30, 54); placePrimary(24, 46);
        Bitmap narrow = Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888);
        drawable.update(key, text, 17); drawable.draw(new Canvas(narrow));
        assertEquals(17 * density, drawable.fittedBottomTextSize(), .001f);
        assertTrue(drawable.fittedBottomScaleX() < wideScale);
        assertTrue(Math.abs(inkBounds(narrow).height() - inkBounds(large).height()) <= 1);
    }
    @Test public void changingOneRowDoesNotCompressTheOtherRow() {
        AdaptiveKeyAdapter.HintDrawable drawable = new AdaptiveKeyAdapter.HintDrawable();
        drawable.update(key, new HintEngine.KeyHint("zh", "iang/uang/iong"), 10);
        drawable.draw(new Canvas(Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888)));
        assertTrue(drawable.fittedBottomScaleX() < 1);
        assertEquals(1f, drawable.fittedTopScaleX(), .001f);
        drawable.update(key, new HintEngine.KeyHint("zh", "ui"), 10);
        drawable.draw(new Canvas(Bitmap.createBitmap(60, 100, Bitmap.Config.ARGB_8888)));
        assertEquals(1f, drawable.fittedTopScaleX(), .001f);
        assertEquals(1f, drawable.fittedBottomScaleX(), .001f);
    }
    @Test public void centerEligibilityNeverDrawsAReplacementGlyph() {
        Bitmap bitmap = render(new HintEngine.KeyHint("", "a", ""), 17);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            assertEquals(0, Color.alpha(bitmap.getPixel(x, y)));
    }

}
