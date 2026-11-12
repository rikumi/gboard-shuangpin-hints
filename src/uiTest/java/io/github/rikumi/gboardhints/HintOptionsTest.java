/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HintOptionsTest {
    @Test public void initialPhaseOnlyKeepsCompoundInitialsAcrossSchemes() {
        for (String scheme : new String[]{"XIAOHE", "Ziranma", "MS", "ABC", "Ziguang", "PinyinJiaJia"}) {
            int count = 0;
            for (char key = 'a'; key <= 'z'; key++) {
                HintEngine.KeyHint hint = HintEngine.hint(scheme, key, "", true, true);
                assertEquals("", hint.center); assertEquals("", hint.top);
                if (!hint.bottom.isEmpty()) {
                    assertTrue(hint.bottom.equals("zh") || hint.bottom.equals("ch") || hint.bottom.equals("sh")); count++;
                }
            }
            assertEquals(3, count);
        }
    }
    @Test public void singleVowelsAreMatchedAndShownOnlyDuringFinalPhase() {
        assertEquals("a", HintEngine.hint("XIAOHE", 'a', "m", true, true).center);
        assertEquals("i", HintEngine.hint("XIAOHE", 'i', "n", true, true).center);
        assertEquals("ü", HintEngine.hint("XIAOHE", 'v', "n", true, true).center);
        assertEquals("", HintEngine.hint("XIAOHE", 'i', "b", true, false).center);
        assertEquals("", HintEngine.hint("XIAOHE", 'i', "f", true, true).center);
        assertEquals("", HintEngine.hint("XIAOHE", 'a', "", false, true).center);
        assertEquals("", HintEngine.hint("XIAOHE", 'v', "n", true, true).top);
        assertEquals("ue", HintEngine.hint("XIAOHE", 't', "n", true, true).bottom);
    }
    @Test public void separatorAndCompletedSyllableSelectCorrectPhase() {
        for (String text : new String[]{"", "ni", "ni'", "ni'hl"}) {
            String initial = HintEngine.pendingInitial(text, "XIAOHE");
            assertEquals("", initial);
            assertEquals("", HintEngine.hint("XIAOHE", 'k', initial, true, true).bottom);
            assertEquals("zh", HintEngine.hint("XIAOHE", 'v', initial, true, true).bottom);
        }
        String initial = HintEngine.pendingInitial("ni'h", "XIAOHE");
        assertEquals("h", initial);
        assertEquals("a", HintEngine.hint("XIAOHE", 'a', initial, true, true).center);
        assertEquals("", HintEngine.hint("XIAOHE", 'v', initial, true, true).top);
    }
    @Test public void zeroInitialVowelsKeepOnlyMatchingLongFinalHints() {
        assertEquals("ang", HintEngine.hint("XIAOHE", 'h', "a", false, false).bottom);
        assertEquals("eng", HintEngine.hint("XIAOHE", 'g', "e", false, false).bottom);
        assertTrue(HintEngine.hint("XIAOHE", 'h', "o", false, false).isEmpty());
        for (String scheme : new String[]{"XIAOHE", "Ziranma", "MS", "ABC", "Ziguang", "PinyinJiaJia"}) {
            for (String prefix : new String[]{"a", "o", "e"}) {
                for (char key = 'a'; key <= 'z'; key++) {
                    HintEngine.KeyHint hint = HintEngine.hint(scheme, key, prefix, true, true);
                    assertEquals("", hint.top);
                    for (String value : hint.bottom.split("/")) if (!value.isEmpty()) {
                        assertTrue(value.length() > 2); assertTrue(value.startsWith(prefix));
                        assertTrue(SchemeData.SYLLABLES.contains(value));
                    }
                }
            }
        }
        for (char key : new char[]{'d', 'j', 'c'})
            assertEquals("", HintEngine.hint("XIAOHE", key, "a", false, false).bottom);
        for (char key : new char[]{'w', 'f'})
            assertEquals("", HintEngine.hint("XIAOHE", key, "e", false, false).bottom);
        assertEquals("", HintEngine.hint("XIAOHE", 'z', "o", false, false).bottom);
    }
    @Test public void zeroInitialCentralLettersFollowLiteralSyllableContinuation() {
        for (String prefix : new String[]{"a", "e", "o"}) {
            String expected = prefix.equals("a") ? "ino" : prefix.equals("e") ? "inr" : "u";
            StringBuilder actual = new StringBuilder();
            for (char key = 'a'; key <= 'z'; key++) {
                HintEngine.KeyHint hint = HintEngine.hint("XIAOHE", key, prefix, true, true);
                actual.append(hint.center);
                assertEquals("", HintEngine.hint("XIAOHE", key, prefix, true, false).center);
            }
            assertEquals(expected, actual.toString());
        }
    }
    @Test public void zeroInitialPhaseUsesOnlyLastSeparatorSegment() {
        for (String prefix : new String[]{"a", "e", "o"}) {
            for (String before : new String[]{"", "ni'", "ni\u2019", "ni "})
                assertEquals(prefix, HintEngine.pendingInitial(before + prefix, "XIAOHE"));
        }
        assertEquals("", HintEngine.pendingInitial("ni'ai", "XIAOHE"));
        assertEquals("", HintEngine.pendingInitial("ni'eng", "XIAOHE"));
        assertEquals("", HintEngine.pendingInitial("ni'ou", "XIAOHE"));
    }

    @Test public void hideCompoundInitialsOverridesAllHintPhasesAndSchemes() {
        for (String scheme : new String[]{"XIAOHE", "Ziranma", "MS", "ABC", "Ziguang", "PinyinJiaJia"})
            for (String initial : new String[]{"", "n", "zh", "a", "e", "o"})
                for (boolean initialsOnly : new boolean[]{false, true})
                    for (boolean singleVowels : new boolean[]{false, true})
                        for (char key : "abcdefghijklmnopqrstuvwxyz;".toCharArray()) {
                            HintEngine.KeyHint original = HintEngine.hint(scheme, key, initial, initialsOnly, singleVowels);
                            HintEngine.KeyHint hidden = HintEngine.hint(scheme, key, initial, initialsOnly, singleVowels, true);
                            assertFalse(hidden.top.equals("zh") || hidden.top.equals("ch") || hidden.top.equals("sh"));
                            assertFalse(hidden.bottom.equals("zh") || hidden.bottom.equals("ch") || hidden.bottom.equals("sh"));
                            assertEquals(original.center, hidden.center);
                            if (!original.bottom.equals("zh") && !original.bottom.equals("ch") && !original.bottom.equals("sh"))
                                assertEquals(original.bottom, hidden.bottom);
                        }
    }

}
