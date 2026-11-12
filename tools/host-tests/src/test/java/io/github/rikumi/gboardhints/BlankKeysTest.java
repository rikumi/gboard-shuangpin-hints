/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class BlankKeysTest {
    private FrameLayout key;
    private TextView letter, symbol;
    private BlankKeyLabels blank;
    @Test public void allHintPolicyChecksPass() { HintEngineTest.main(new String[0]); }
    @Before public void setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        key = new FrameLayout(activity);
        letter = new TextView(activity); letter.setText("N"); letter.setTextSize(22);
        symbol = new TextView(activity); symbol.setText("?"); symbol.setTextSize(10);
        key.addView(letter); key.addView(symbol); key.setContentDescription("N");
        activity.setContentView(key);
        blank = new BlankKeyLabels();
    }
    @Test public void blankKeepsTextAndAccessibility() {
        blank.apply(key, true, 'n');
        assertEquals(0f, letter.getAlpha(), 0f);
        assertEquals(0f, symbol.getAlpha(), 0f);
        assertEquals("N", letter.getText().toString());
        assertEquals("N", key.getContentDescription());
        assertEquals('n', AdaptiveKeyAdapter.key(key, null));
    }
    @Test public void blankHidesUpperCornerDigits() {
        symbol.setText("1");
        blank.apply(key, true, 'n');
        assertEquals(0f, letter.getAlpha(), 0f);
        assertEquals(0f, symbol.getAlpha(), 0f);
    }
    @Test public void repeatedUpdatesRestoreOriginalAlpha() {
        letter.setAlpha(.8f); symbol.setAlpha(.6f);
        blank.apply(key, true, 'n');
        blank.apply(key, true, 'n');
        assertEquals(0f, letter.getAlpha(), 0f);
        assertEquals(0f, symbol.getAlpha(), 0f);
        blank.restore();
        assertEquals(.8f, letter.getAlpha(), 0f);
        assertEquals(.6f, symbol.getAlpha(), 0f);
    }
    @Test public void reusedNumericAndFunctionKeysRestoreTheirLabels() {
        blank.apply(key, true, 'n');
        letter.setText("1");
        blank.apply(key, true, '1');
        assertEquals(1f, letter.getAlpha(), 0f);
        assertEquals(1f, symbol.getAlpha(), 0f);
        blank.apply(key, true, (char)0);
        assertEquals(1f, letter.getAlpha(), 0f);
    }
    @Test public void turningOffRestoresWithoutChangingBoundsOrText() {
        key.layout(0, 0, 60, 90);
        blank.apply(key, true, 'n');
        blank.apply(key, false, 'n');
        assertEquals(60, key.getWidth()); assertEquals(90, key.getHeight());
        assertEquals("N", letter.getText().toString()); assertEquals(1f, letter.getAlpha(), 0f);
    }
    @Test public void metadataPrimaryWinsOverSecondaryLetter() throws Exception {
        MetadataKey custom = new MetadataKey(key.getContext());
        TextView secondary = new TextView(key.getContext()); secondary.setText("a"); custom.addView(secondary);
        java.lang.reflect.Field field = AdaptiveKeyAdapter.metadataField(MetadataKey.class);
        assertNotNull(field);
        assertEquals((char)0, AdaptiveKeyAdapter.key(custom, field));
        custom.def.labels[0] = "Q";
        assertEquals('q', AdaptiveKeyAdapter.key(custom, field));
    }

    @Test public void numberHintHidingPreservesNativePrimaryAndNonNumericHints() {
        key.layout(0, 0, 60, 90); letter.layout(0, 0, 60, 90);
        symbol.layout(42, 0, 60, 20); symbol.setAlpha(.6f); letter.setAlpha(.8f);
        for (String digit : new String[]{"1", "٢"}) {
            symbol.setText(digit);
            blank.apply(key, false, (char)0, false, false, true);
            assertEquals(0f, symbol.getAlpha(), 0f); assertEquals(.8f, letter.getAlpha(), 0f);
            assertEquals("N", letter.getText().toString());
            blank.restore(); assertEquals(.6f, symbol.getAlpha(), 0f);
        }
        symbol.setText("?"); blank.apply(key, false, (char)0, false, false, true);
        assertEquals(.6f, symbol.getAlpha(), 0f);
    }
    @Test public void numberHintHidingLeavesNumericKeysAndNonCornerDigitsVisible() {
        key.layout(0, 0, 60, 90); letter.layout(0, 0, 60, 90); letter.setText("1");
        symbol.setText("2"); symbol.setGravity(android.view.Gravity.CENTER);
        symbol.layout(0, 0, 60, 90);
        blank.apply(key, false, '1', false, false, true);
        assertEquals(1f, letter.getAlpha(), 0f); assertEquals(1f, symbol.getAlpha(), 0f);
        symbol.layout(42, 70, 60, 90);
        blank.apply(key, false, '1', false, false, true);
        assertEquals(1f, symbol.getAlpha(), 0f);
    }
    @Test public void numberToggleAndBlankModeShareRestorationWithoutAlphaConflicts() {
        key.layout(0, 0, 60, 90); letter.layout(0, 0, 60, 90);
        symbol.setText("1"); symbol.layout(0, 0, 60, 90);
        symbol.setGravity(android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        blank.apply(key, true, 'n', false, true, true);
        assertEquals(1f, letter.getAlpha(), 0f); assertEquals(0f, symbol.getAlpha(), 0f);
        blank.apply(key, true, 'n', false, false, false);
        assertEquals(0f, letter.getAlpha(), 0f); assertEquals(0f, symbol.getAlpha(), 0f);
        blank.apply(key, false, 'n', false, false, true);
        assertEquals(1f, letter.getAlpha(), 0f); assertEquals(0f, symbol.getAlpha(), 0f);
        blank.restore(); assertEquals(1f, symbol.getAlpha(), 0f);
    }
    public static final class Metadata { public CharSequence[] labels = {"1", "a"}; }
    public static final class MetadataKey extends FrameLayout {
        public Metadata def = new Metadata();
        public MetadataKey(android.content.Context context) { super(context); }
    }
}
