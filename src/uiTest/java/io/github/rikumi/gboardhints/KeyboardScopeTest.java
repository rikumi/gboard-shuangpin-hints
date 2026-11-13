/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class KeyboardScopeTest {
    private org.robolectric.android.controller.ActivityController<Activity> activity;
    private HintController controller;
    private final List<FrameLayout> keys = new ArrayList<>();
    private final List<TextView> letters = new ArrayList<>();
    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout root = new FrameLayout(activity.get()); activity.get().setContentView(root);
        root.layout(0, 0, 600, 300);
        controller = new HintController();
        ReflectionHelpers.setField(controller, "blankEnabled", true);
        for (char c = 'a'; c <= 'z'; c++) {
            FrameLayout key = new FrameLayout(activity.get());
            TextView letter = new TextView(activity.get()); letter.setText(String.valueOf(c));
            letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
            key.addView(letter); root.addView(key); keys.add(key); letters.add(letter);
            int i = c - 'a'; key.layout((i % 10) * 60, (i / 10) * 90, (i % 10) * 60 + 60, (i / 10) * 90 + 80);
            letter.layout(0, 0, 60, 80); controller.observe(key, null);
        }
        controller.start(new EditorInfo());
        controller.schemeRead("shuangpin_flypy"); controller.decoderStarted(true); idle();
        assertEquals(0f, letters.get(0).getAlpha(), 0f); assertTrue(hint('k'));
    }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private boolean hint(char c) {
        Map<View, ?> states = ReflectionHelpers.getField(controller, "keys");
        return ReflectionHelpers.getField(states.get(keys.get(c - 'a')), "added");
    }
    @After public void destroy() { controller.detach(); activity.pause().stop().destroy(); }
    @Test public void englishImeActivationRestoresAllLabelsAndRemovesHints() {
        controller.imeActivated(); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
        assertFalse(hint('k'));
    }
    @Test public void fullPinyinDisablesShuangpinFeatures() {
        controller.schemeRead("pinyin"); controller.decoderStarted(true); idle();
        assertEquals(1f, letters.get(0).getAlpha(), 0f); assertFalse(hint('k'));
    }
    @Test public void otherChineseDecodersDoNotKeepShuangpinFeatures() {
        controller.decoderStarted(false); idle();
        assertEquals(1f, letters.get(0).getAlpha(), 0f); assertFalse(hint('k'));
    }
    @Test public void returningToShuangpinRestoresIndependentBlankSwitch() {
        controller.imeActivated(); idle();
        controller.decoderStarted(true); idle();
        assertEquals(0f, letters.get(0).getAlpha(), 0f); assertTrue(hint('k'));
        ReflectionHelpers.setField(controller, "enabled", false); controller.refreshLater(); idle();
        assertEquals(0f, letters.get(0).getAlpha(), 0f); assertFalse(hint('k'));
    }
    @Test public void symbolsPageRetainsItsOriginalLabels() {
        for (TextView letter : letters) letter.setText("1");
        controller.refreshLater(); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
        assertFalse(hint('k'));
    }
    @Test public void nativeCenterFontStaysUnchangedAcrossShuangpinAndEnglish() {
        ReflectionHelpers.setField(controller, "blankEnabled", false); controller.refreshLater(); idle();
        assertEquals(24f, letters.get('k' - 'a').getTextSize(), .01f);
        controller.imeActivated(); idle();
        assertEquals(24f, letters.get('k' - 'a').getTextSize(), .01f);
    }
    @Test public void singleVowelFilterWorksWithAndWithoutBlankingAndRestores() {
        ReflectionHelpers.setField(controller, "singleVowels", true);
        for (boolean blank : new boolean[]{false, true}) {
            ReflectionHelpers.setField(controller, "blankEnabled", blank);
            controller.engineComposition("ni'm"); idle();
            assertEquals(1f, letters.get(0).getAlpha(), 0f);
            assertEquals(0f, letters.get('v' - 'a').getAlpha(), 0f);
            assertFalse(hint('a')); assertFalse(hint('v'));
            controller.engineComposition("ni'ma"); idle();
            assertEquals(blank ? 0f : 1f, letters.get(0).getAlpha(), 0f);
        }
        controller.imeActivated(); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
    }
    @Test public void initialsOnlyUpdatesAfterCompletedSyllableAndFinalFilterCanBeDisabled() {
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        controller.refreshLater(); idle();
        assertTrue(hint('v')); assertFalse(hint('k'));
        controller.engineComposition("ni'h"); idle();
        assertTrue(hint('k'));
        ReflectionHelpers.setField(controller, "singleVowels", true); controller.refreshLater(); idle();
        assertEquals(1f, letters.get(0).getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "singleVowels", false); controller.refreshLater(); idle();
        assertEquals(1f, letters.get(0).getAlpha(), 0f);
        controller.engineComposition("ni'hl"); idle();
        assertFalse(hint('k')); assertTrue(hint('v'));
    }

    @Test public void zeroInitialLettersRenderAndRestoreWithBothBlankModes() {
        ReflectionHelpers.setField(controller, "singleVowels", true);
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        for (boolean blank : new boolean[]{false, true}) {
            ReflectionHelpers.setField(controller, "blankEnabled", blank);
            controller.engineComposition("ni'a"); idle();
            assertTrue(hint('h')); assertFalse(hint('i')); assertFalse(hint('n')); assertFalse(hint('o'));
            for (char key : new char[]{'i', 'n', 'o'}) assertEquals(1f, letters.get(key - 'a').getAlpha(), 0f);
            assertEquals(0f, letters.get(0).getAlpha(), 0f);
            assertFalse(hint('j')); assertFalse(hint('v'));
            controller.engineComposition("ni'ai"); idle();
            assertTrue(hint('v')); assertFalse(hint('h'));
            assertEquals(0f, letters.get('i' - 'a').getAlpha(), 0f);
        }
    }

    @Test public void spacedMultiSyllablePreeditKeepsFinalFilterAndNativeFontSize() {
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        ReflectionHelpers.setField(controller, "singleVowels", true);
        for (String preedit : new String[]{"ni m", "ni hao m ", "ni  hao  m  "}) {
            controller.engineComposition(preedit); idle();
            assertFalse(hint('a')); assertFalse(hint('v'));
            assertEquals(1f, letters.get(0).getAlpha(), 0f);
            assertEquals(0f, letters.get('v' - 'a').getAlpha(), 0f);
            for (TextView letter : letters) assertEquals(24f, letter.getTextSize(), .01f);
        }
        controller.engineComposition("ni hao ma "); idle();
        for (TextView letter : letters) {
            assertEquals(1f, letter.getAlpha(), 0f);
            assertEquals(24f, letter.getTextSize(), .01f);
        }
    }

    @Test public void microsoftSemicolonKeepsItsNativeSizeWhenHintsAreDrawn() {
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        controller.schemeRead("shuangpin_ms");
        TextView semicolon = letters.get('z' - 'a'); semicolon.setText(";");
        semicolon.setTextSize(TypedValue.COMPLEX_UNIT_PX, 29);
        controller.refreshLater(); idle();
        assertTrue(hint('z')); assertEquals(29f, semicolon.getTextSize(), .001f);
        controller.engineComposition("ni n "); idle();
        assertTrue(hint('z')); assertEquals(29f, semicolon.getTextSize(), .001f);
        controller.imeActivated(); idle();
        assertEquals(29f, semicolon.getTextSize(), .001f);
    }

    @Test public void matchingNativeKeysKeepOriginalTypefaceColorCaseAndCornerBlanking() {
        ReflectionHelpers.setField(controller, "singleVowels", true);
        TextView nativeA = letters.get(0); nativeA.setText("A");
        nativeA.setTypeface(android.graphics.Typeface.MONOSPACE); nativeA.setTextColor(0xff829b73);
        nativeA.setTextSize(TypedValue.COMPLEX_UNIT_PX, 31);
        TextView corner = new TextView(activity.get()); corner.setText("1"); keys.get(0).addView(corner);
        for (boolean blank : new boolean[]{false, true}) {
            ReflectionHelpers.setField(controller, "blankEnabled", blank);
            controller.engineComposition("ni\u0085hao\u2009m "); idle();
            assertEquals(1f, nativeA.getAlpha(), 0f); assertFalse(hint('a'));
            assertEquals("A", nativeA.getText().toString());
            assertEquals(android.graphics.Typeface.MONOSPACE, nativeA.getTypeface());
            assertEquals(0xff829b73, nativeA.getCurrentTextColor());
            assertEquals(31f, nativeA.getTextSize(), .001f);
            assertEquals(blank ? 0f : 1f, corner.getAlpha(), 0f);
        }
        controller.engineComposition("ni n"); idle();
        TextView nativeV = letters.get('v' - 'a');
        assertEquals(1f, nativeV.getAlpha(), 0f); assertEquals("v", nativeV.getText().toString());
        assertFalse(hint('v'));
        controller.imeActivated(); idle(); assertEquals(1f, corner.getAlpha(), 0f);
    }

    @Test public void hidingCompoundInitialsRemovesBothTopAndBottomAndRestoresOnDisable() {
        for (boolean blank : new boolean[]{false, true}) {
            ReflectionHelpers.setField(controller, "blankEnabled", blank);
            for (boolean onlyInitials : new boolean[]{false, true}) {
                ReflectionHelpers.setField(controller, "initialsOnly", onlyInitials);
                ReflectionHelpers.setField(controller, "hideCompoundInitials", false);
                controller.engineComposition(""); idle(); assertTrue(hint('i'));
                ReflectionHelpers.setField(controller, "hideCompoundInitials", true);
                controller.refreshLater(); idle();
                assertFalse(hint('i')); assertFalse(hint('u'));
                assertEquals(!onlyInitials, hint('v'));
                controller.engineComposition("ni n"); idle(); assertTrue(hint('k'));
                controller.engineComposition("niha"); idle(); assertFalse(hint('i'));
                ReflectionHelpers.setField(controller, "hideCompoundInitials", false);
                controller.refreshLater(); idle(); assertTrue(hint('i'));
            }
        }
    }

    @Test public void formattedInternalPhraseFiltersThenClearImmediatelyRestoresInitialMode() {
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        ReflectionHelpers.setField(controller, "singleVowels", true);
        controller.engineComposition("nihaon", "ni hao n"); idle();
        assertEquals(1f, letters.get('i' - 'a').getAlpha(), 0f);
        assertEquals(0f, letters.get('o' - 'a').getAlpha(), 0f);
        assertTrue(hint('k'));
        controller.engineComposition("nihaoni", "ni hao ni"); idle();
        assertFalse(hint('k')); assertTrue(hint('v'));
        controller.engineComposition("nihaon", "ni hao n"); idle();
        controller.engineComposition((CharSequence)null);
        DynamicHintState state = ReflectionHelpers.getField(controller, "compositionState");
        assertEquals("Reset must apply in the same main-thread call", "", state.initial());
        idle(); assertFalse(hint('k')); assertTrue(hint('v'));
        controller.engineComposition("g", "g"); idle();
        assertTrue(hint('k'));
        controller.engineComposition("", ""); idle();
        assertFalse(hint('k')); assertTrue(hint('v'));
    }

    @Test public void queuedWorkerSnapshotCannotRestoreADeletedInitial() throws Exception {
        Thread worker = new Thread(() -> controller.engineComposition("nihaon", "ni hao n"));
        worker.start(); worker.join();
        controller.engineComposition((CharSequence)null); idle();
        DynamicHintState state = ReflectionHelpers.getField(controller, "compositionState");
        assertEquals("", state.initial());
        worker = new Thread(() -> controller.engineComposition("nihaog", "ni hao g"));
        worker.start(); worker.join();
        controller.finish(); idle(); assertEquals("", state.initial());
    }

    @Test public void globalNumberHintsHideAcrossEnglishFullPinyinAndNonLatinKeyboards() {
        TextView corner = new TextView(activity.get()); corner.setText("1"); corner.setTextSize(10);
        FrameLayout.LayoutParams placement = new FrameLayout.LayoutParams(18, 18,
                android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        corner.setGravity(android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        keys.get(0).addView(corner, placement); corner.layout(42, 0, 60, 18);
        ReflectionHelpers.setField(controller, "hideNumberHints", true);
        controller.imeActivated(); idle();
        assertEquals(0f, corner.getAlpha(), 0f); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        controller.schemeRead("pinyin"); controller.decoderStarted(true); idle();
        assertEquals(0f, corner.getAlpha(), 0f); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        for (TextView letter : letters) letter.setText("あ");
        controller.refreshLater(); idle();
        assertEquals(0f, corner.getAlpha(), 0f); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "enabled", false);
        for (TextView letter : letters) letter.setText("2");
        controller.refreshLater(); idle();
        assertEquals(0f, corner.getAlpha(), 0f); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "hideNumberHints", false); controller.refreshLater(); idle();
        assertEquals(1f, corner.getAlpha(), 0f);
    }

    @Test public void idleInitialOnlyModeHidesUivAndRestoresThemDuringFinalPhase() {
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        controller.engineComposition("", ""); idle();
        for (char key : new char[]{'u','i','v'}) assertEquals(0f, letters.get(key - 'a').getAlpha(), 0f);
        controller.engineComposition("n", "n"); idle();
        for (char key : new char[]{'u','i','v'}) assertEquals(1f, letters.get(key - 'a').getAlpha(), 0f);
        controller.engineComposition("ni", "ni"); idle();
        for (char key : new char[]{'u','i','v'}) assertEquals(0f, letters.get(key - 'a').getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "initialsOnly", false); controller.refreshLater(); idle();
        for (char key : new char[]{'u','i','v'}) assertEquals(1f, letters.get(key - 'a').getAlpha(), 0f);
    }

    @Test public void nativeDrawCannotRestoreHiddenLettersAndDisablingRestoresOriginalAlpha() {
        TextView label = letters.get(0);
        label.setAlpha(1f); // Gboard's first draw callback reapplies its native style.
        controller.enforceKeyState(keys.get(0));
        assertEquals(0f, label.getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        controller.refreshLater(); idle();
        controller.enforceKeyState(keys.get(0));
        assertEquals(1f, label.getAlpha(), 0f);
    }
    @Test public void pendingFirstFrameRenderIsAppliedBeforeChildrenDraw() {
        ReflectionHelpers.setField(controller, "blankEnabled", false);
        controller.refreshLater(); idle();
        ReflectionHelpers.setField(controller, "blankEnabled", true);
        controller.refreshLater();
        keys.get(0).getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(0f, letters.get(0).getAlpha(), 0f);
    }

    @Test public void letterVisibilitySwitchesIndependentlyWhenThePendingInitialChanges() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "defaultLetters", false);
        ReflectionHelpers.setField(controller, "finalsLetters", true);
        controller.engineComposition(""); idle(); assertEquals(0f, letters.get(0).getAlpha(), 0f);
        controller.engineComposition("n"); idle(); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        controller.engineComposition("ni"); idle(); assertEquals(0f, letters.get(0).getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "defaultLetters", true);
        ReflectionHelpers.setField(controller, "finalsLetters", false);
        controller.engineComposition("n"); idle(); assertEquals(0f, letters.get(0).getAlpha(), 0f);
        controller.engineComposition(""); idle(); assertEquals(1f, letters.get(0).getAlpha(), 0f);
        controller.imeActivated(); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
    }
    @Test public void pendingStateDoesNotDependOnEnablingFinalHintFiltering() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "dynamic", false);
        ReflectionHelpers.setField(controller, "defaultLetters", true);
        ReflectionHelpers.setField(controller, "finalsLetters", false);
        controller.engineComposition("n"); idle(); assertEquals(0f, letters.get(0).getAlpha(), 0f);
        controller.engineComposition("ni"); idle(); assertEquals(1f, letters.get(0).getAlpha(), 0f);
    }
    @Test public void hiddenWaitingLettersDoNotLeakMatchingVowelsOrHideCornerDigits() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "finalsLetters", false);
        ReflectionHelpers.setField(controller, "singleVowels", true);
        TextView corner = new TextView(activity.get()); corner.setText("1"); keys.get(0).addView(corner);
        controller.engineComposition("m"); idle();
        assertEquals(0f, letters.get(0).getAlpha(), 0f);
        assertEquals(1f, corner.getAlpha(), 0f);
        ReflectionHelpers.setField(controller, "finalsLetters", true); controller.refreshLater(); idle();
        assertEquals(1f, letters.get(0).getAlpha(), 0f);
        assertEquals(0f, letters.get('v' - 'a').getAlpha(), 0f);
    }

    @Test public void forcedLowerInitialsAlsoApplyWhenWaitingHintsAreNotFiltered() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "dynamic", false);
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        ReflectionHelpers.setField(controller, "initialsBelow", true);
        controller.engineComposition("n"); idle();
        Map<View, ?> states = ReflectionHelpers.getField(controller, "keys");
        Object state = states.get(keys.get('v' - 'a'));
        Object drawable = ReflectionHelpers.getField(state, "hint");
        HintEngine.KeyHint text = ReflectionHelpers.getField(drawable, "hint");
        assertEquals("", text.top);
        assertTrue(text.bottom.startsWith("zh"));
    }

    @Test public void availableNativeVowelsFilterIndependentlyOfDynamicHintsAndHintMasterSwitch() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "dynamic", false);
        ReflectionHelpers.setField(controller, "finalsLetters", true);
        ReflectionHelpers.setField(controller, "singleVowels", true);
        for (boolean hints : new boolean[]{true, false}) {
            ReflectionHelpers.setField(controller, "enabled", hints);
            controller.engineComposition("m"); idle();
            assertEquals(1f, letters.get(0).getAlpha(), 0f);
            assertEquals(0f, letters.get('v' - 'a').getAlpha(), 0f);
            controller.engineComposition("a"); idle();
            assertEquals(1f, letters.get('i' - 'a').getAlpha(), 0f);
            assertEquals(0f, letters.get('a' - 'a').getAlpha(), 0f);
        }
    }
    @Test public void letterFontSizeIsGlobalAndRestoresWhenDisabledOrKeyboardFinishes() {
        ReflectionHelpers.setField(controller, "keyLetterSizeEnabled", true);
        ReflectionHelpers.setField(controller, "keyLetterSize", 32);
        controller.imeActivated(); idle();
        for (TextView letter : letters) assertEquals(32f, letter.getTextSize(), .01f);
        ReflectionHelpers.setField(controller, "keyLetterSizeEnabled", false); controller.refreshLater(); idle();
        for (TextView letter : letters) assertEquals(24f, letter.getTextSize(), .01f);
        ReflectionHelpers.setField(controller, "keyLetterSizeEnabled", true); controller.refreshLater(); idle();
        controller.finish(); idle();
        for (TextView letter : letters) assertEquals(24f, letter.getTextSize(), .01f);
    }

    @Test public void defaultLetterModesUseSchemeMappingsAndRestoreOnStateOrLanguageChange() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "defaultVisibilityConfigured", true);
        ReflectionHelpers.setField(controller, "finalsLetters", true);
        ReflectionHelpers.setField(controller, "initialsOnly", true);
        String[] nativeSchemes = {"shuangpin_flypy", "shuangpin_abc", "shuangpin_ziguang"};
        String[] schemes = {"XIAOHE", "ABC", "Ziguang"};
        for (int n = 0; n < nativeSchemes.length; n++) {
            controller.schemeRead(nativeSchemes[n]); controller.decoderStarted(true);
            for (int mode = StateOptions.DEFAULT_ALL; mode <= StateOptions.DEFAULT_HIDDEN; mode++) {
                ReflectionHelpers.setField(controller, "defaultVisibility", mode);
                controller.engineComposition(""); idle();
                for (char key = 'a'; key <= 'z'; key++) {
                    boolean hidden = mode == StateOptions.DEFAULT_HIDDEN || mode == StateOptions.DEFAULT_HIDE_VOWELS
                            && "aeoiuv".indexOf(key) >= 0 && SchemeData.initial(schemes[n], key) != null;
                    assertEquals(schemes[n] + ":" + mode + ":" + key, hidden ? 0f : 1f, letters.get(key - 'a').getAlpha(), 0f);
                }
                controller.engineComposition("n"); idle();
                for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
            }
        }
        controller.imeActivated(); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
    }
    @Test public void defaultSelectiveNativeLetterHidingDoesNotRequireHintOverlays() {
        ReflectionHelpers.setField(controller, "stateOptionsConfigured", true);
        ReflectionHelpers.setField(controller, "defaultVisibilityConfigured", true);
        ReflectionHelpers.setField(controller, "defaultVisibility", StateOptions.DEFAULT_HIDE_VOWELS);
        ReflectionHelpers.setField(controller, "enabled", false);
        controller.engineComposition(""); idle();
        for (char key : new char[]{'u', 'i', 'v'}) assertEquals(0f, letters.get(key - 'a').getAlpha(), 0f);
        assertEquals(1f, letters.get(0).getAlpha(), 0f);
        assertFalse(hint('k'));
        controller.schemeRead("pinyin"); controller.decoderStarted(true); idle();
        for (TextView letter : letters) assertEquals(1f, letter.getAlpha(), 0f);
    }

}
