/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.graphics.Typeface;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class DefaultFontTest {
    @Test public void hintUsesDefaultTypefaceWithoutModifyingNativeText() {
        TextView key = new TextView(RuntimeEnvironment.getApplication()); key.setText("N");
        key.setTypeface(Typeface.MONOSPACE); key.setTextColor(0xff315bad); key.setTextSize(29);
        float size = key.getTextSize(); key.layout(0, 0, 48, 60);
        AdaptiveKeyAdapter.HintDrawable hint = new AdaptiveKeyAdapter.HintDrawable();
        hint.update(key, HintEngine.hint("XIAOHE", 'n', ""), 10);
        assertEquals(Typeface.DEFAULT, hint.hintTypeface());
        assertEquals(Typeface.MONOSPACE, key.getTypeface());
        assertEquals(0xff315bad, key.getCurrentTextColor()); assertEquals(size, key.getTextSize(), 0f);
    }
    @Test public void moduleDoesNotBundleAnyFontAssets() throws Exception {
        assertEquals(0, RuntimeEnvironment.getApplication().getAssets().list("fonts").length);
    }
}
