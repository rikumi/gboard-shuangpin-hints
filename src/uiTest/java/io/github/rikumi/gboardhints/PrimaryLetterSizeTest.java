/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PrimaryLetterSizeTest {
    private TextView label(Context context, String text, float pixels) {
        TextView label = new TextView(context); label.setText(text); label.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels); return label;
    }
    private float pixels(TextView label, int sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, label.getResources().getDisplayMetrics());
    }
    @Test public void resizesMainLetterOnlyAndRestoresNativeFontAndColor() {
        Context ctx = RuntimeEnvironment.getApplication(); FrameLayout key = new KeyboardKeyFixture(ctx);
        TextView main = label(ctx, "Q", 24), corner = label(ctx, "1", 10); key.addView(main); key.addView(corner);
        main.setTypeface(Typeface.MONOSPACE); main.setTextColor(0xff36aa92);
        PrimaryLetterSize size = new PrimaryLetterSize();
        size.apply(key, true, 32);
        assertEquals(pixels(main,32), main.getTextSize(), .01f); assertEquals(10f, corner.getTextSize(), .01f);
        assertEquals(Typeface.MONOSPACE, main.getTypeface()); assertEquals(0xff36aa92, main.getCurrentTextColor());
        size.apply(key, true, 12); size.restore();
        assertEquals(24f, main.getTextSize(), .01f); assertEquals(Typeface.MONOSPACE, main.getTypeface());
    }
    @Test public void nativeStyleRequestsKeepOverrideAndUpdateTheRestorationValue() {
        Context ctx = RuntimeEnvironment.getApplication(); TextView main = label(ctx, "a", 24);
        FrameLayout key = new KeyboardKeyFixture(ctx); key.addView(main);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,30);
        Float sp = PrimaryLetterSize.nativeRequest(main, TypedValue.COMPLEX_UNIT_PX, 26);
        assertEquals(30f, sp, .01f);
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        size.restore(); assertEquals(26f, main.getTextSize(), .01f);
        assertNull(PrimaryLetterSize.nativeRequest(main, TypedValue.COMPLEX_UNIT_PX, 28));
    }
    @Test public void supportsUnicodeLettersAndSemicolonButLeavesFunctionLabelsAlone() {
        Context ctx = RuntimeEnvironment.getApplication();
        for (String text : new String[]{"ä", "é", "e\u0301", "я", "あ", ";"}) {
            TextView main = label(ctx,text,24); FrameLayout key = new KeyboardKeyFixture(ctx); key.addView(main); PrimaryLetterSize size = new PrimaryLetterSize();
            size.apply(key,true,30); assertEquals(pixels(main,30), main.getTextSize(), .01f); size.restore();
        }
        for (String text : new String[]{"123", "1", "中文", "Enter", "↵", "+"}) {
            TextView main = label(ctx,text,24); FrameLayout key = new KeyboardKeyFixture(ctx); key.addView(main); PrimaryLetterSize size = new PrimaryLetterSize();
            size.apply(key,true,30); assertEquals(24f, main.getTextSize(), .01f); size.restore();
        }
    }
    @Test public void keyRebindingToDigitsDoesNotRetainLetterOverride() {
        Context ctx = RuntimeEnvironment.getApplication(); TextView main = label(ctx,"a",24);
        FrameLayout key = new KeyboardKeyFixture(ctx); key.addView(main);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,30); main.setText("1");
        assertNull(PrimaryLetterSize.nativeRequest(main,TypedValue.COMPLEX_UNIT_PX,14));
        main.setTextSize(TypedValue.COMPLEX_UNIT_PX,14); size.apply(key,true,30);
        assertEquals(14f, main.getTextSize(), .01f); size.restore();
    }
    @Test public void reducingMainFontDoesNotAccidentallySelectALetterCorner() {
        Context ctx = RuntimeEnvironment.getApplication(); FrameLayout key = new KeyboardKeyFixture(ctx);
        TextView main = label(ctx,"a",24), corner = label(ctx,"á",14); key.addView(main); key.addView(corner);
        PrimaryLetterSize size = new PrimaryLetterSize();
        size.apply(key,true,12); size.apply(key,true,12);
        assertEquals(pixels(main,12), main.getTextSize(), .01f); assertEquals(14f,corner.getTextSize(),.01f);
        size.restore(); assertEquals(24f,main.getTextSize(),.01f);
    }
    @Test @Config(sdk = 23) public void fontOverrideAlsoWorksOnAndroidSix() {
        Context ctx = RuntimeEnvironment.getApplication(); TextView main = label(ctx,"a",24);
        FrameLayout key = new KeyboardKeyFixture(ctx); key.addView(main);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,30);
        assertEquals(pixels(main,30),main.getTextSize(),.01f);
        size.restore(); assertEquals(24f,main.getTextSize(),.01f);
    }

    @Test public void digitPrimaryWithALetterCornerDoesNotResizeTheCorner() {
        Context ctx = RuntimeEnvironment.getApplication(); FrameLayout key = new KeyboardKeyFixture(ctx);
        TextView main = label(ctx,"1",24), corner = label(ctx,"q",10); key.addView(main); key.addView(corner);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,30);
        assertEquals(24f,main.getTextSize(),.01f); assertEquals(10f,corner.getTextSize(),.01f);
        size.restore();
    }

    @Test public void singleCharacterCandidatesAndToolbarLabelsKeepTheirNativeSize() {
        Context ctx = RuntimeEnvironment.getApplication();
        for (String text : new String[]{"字", "你", "a", "é", ";"}) {
            TextView candidate = label(ctx, text, 18);
            FrameLayout holder = new FrameLayout(ctx); holder.addView(candidate);
            PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(holder, true, 40);
            assertEquals(18f, candidate.getTextSize(), .01f);
            assertNull(PrimaryLetterSize.nativeRequest(candidate, TypedValue.COMPLEX_UNIT_PX, 20));
            KeyboardKeyFixture header = new KeyboardKeyFixture(ctx); header.position = "key_pos_header_action";
            holder.removeView(candidate); header.addView(candidate); size.apply(header, true, 40);
            assertEquals(18f, candidate.getTextSize(), .01f);
            size.restore();
        }
    }
    private static class CandidatesHolderView extends FrameLayout { CandidatesHolderView(Context ctx) { super(ctx); } }
    @Test public void movingAKeyIntoTheCandidateAreaRestoresAndStopsOverridingItsLabel() {
        Context ctx = RuntimeEnvironment.getApplication();
        KeyboardKeyFixture key = new KeyboardKeyFixture(ctx); TextView main = label(ctx, "a", 24); key.addView(main);
        FrameLayout candidates = new CandidatesHolderView(ctx);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key, true, 40);
        assertEquals(pixels(main,40), main.getTextSize(), .01f);
        candidates.addView(key); main.setText("字"); size.apply(key, true, 40);
        assertEquals(24f, main.getTextSize(), .01f);
        assertNull(PrimaryLetterSize.nativeRequest(main, TypedValue.COMPLEX_UNIT_PX, 18));
        candidates.removeView(key); main.setText("a"); size.apply(key, true, 40);
        candidates.addView(key);
        assertNull(PrimaryLetterSize.nativeRequest(main, TypedValue.COMPLEX_UNIT_PX, 18));
        main.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18); size.apply(key, true, 40);
        assertEquals(18f, main.getTextSize(), .01f);
        size.restore(); assertEquals(18f, main.getTextSize(), .01f);
    }
    @Test public void recycledLabelOutsideItsRegisteredKeyKeepsNativeCandidateStyle() {
        Context ctx = RuntimeEnvironment.getApplication(); KeyboardKeyFixture key = new KeyboardKeyFixture(ctx);
        TextView main = label(ctx,"a",24); key.addView(main);
        PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,40);
        key.removeView(main); FrameLayout candidate = new FrameLayout(ctx); candidate.addView(main); main.setText("字");
        assertNull(PrimaryLetterSize.nativeRequest(main,TypedValue.COMPLEX_UNIT_PX,18));
        main.setTextSize(TypedValue.COMPLEX_UNIT_PX,18); size.apply(key,true,40);
        assertEquals(18f, main.getTextSize(), .01f);
        size.restore(); assertEquals(18f, main.getTextSize(), .01f);
    }

    @Test public void nativeGridKanaAndSemicolonPositionsRemainEligible() {
        Context ctx = RuntimeEnvironment.getApplication();
        for (String position : new String[]{"C01", "C10", "D01", "E05_dup", "key_pos_0_0", "key_pos_1_3_dup", "key_pos_ja_12keys_sub_1",
                "key_pos_ja_godan_a", "key_pos_punctuation_1"}) {
            KeyboardKeyFixture key = new KeyboardKeyFixture(ctx); key.position = position;
            TextView main = label(ctx,position.contains("punctuation") ? ";" : "a",24); key.addView(main);
            PrimaryLetterSize size = new PrimaryLetterSize(); size.apply(key,true,32);
            assertEquals(position,pixels(main,32),main.getTextSize(),.01f); size.restore();
        }
    }

}
