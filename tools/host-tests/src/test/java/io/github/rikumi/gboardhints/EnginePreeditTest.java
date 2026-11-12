/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EnginePreeditTest {
    private final DynamicHintState state = new DynamicHintState();
    private void engine(CharSequence text) { state.engineInitial(HintEngine.pendingInitial(text, "XIAOHE")); }
    private void client(CharSequence text) { state.clientInitial(HintEngine.pendingInitial(text, "XIAOHE")); }
    private String bottom(char key) { return HintEngine.hint("XIAOHE", key, state.initial()).bottom; }
    @Test public void internalPreeditFiltersEvenWhenEditorOnlyReceivesChinese() {
        engine("n"); client("你");
        assertEquals("ing", bottom('k'));
        engine("g"); client("个");
        assertEquals("uai", bottom('k'));
    }
    @Test public void missingEditorComposingRegionDoesNotEraseEngineInitial() {
        engine("n"); client(null); client("");
        assertEquals("ing", bottom('k'));
    }
    @Test public void engineCompletionAndCommitRestoreTheFullFinalMap() {
        engine("n"); engine("ni");
        assertEquals("ing/uai", bottom('k'));
        engine("g"); engine("");
        assertEquals("ing/uai", bottom('k'));
    }
    @Test public void deletingFinalRestoresInitialAndPhraseInputFollowsLatestSyllable() {
        engine("ni"); engine("n"); assertEquals("ing", bottom('k'));
        engine("ni g"); assertEquals("uai", bottom('k'));
        engine("ni'v"); assertEquals("", bottom('n'));
        engine("ni'zh"); assertEquals("ing/uai", bottom('k'));
        engine(null); assertEquals("ing/uai", bottom('k'));
    }
    @Test public void inputLifecycleResetAllowsClientFallbackAgain() {
        engine("n"); state.reset(); client("g");
        assertEquals("uai", bottom('k'));
    }
    @Test public void modernDecoderReadsUnconvertedCacheInsteadOfDisplayText() throws Exception {
        ModernDecoder decoder = new ModernDecoder();
        EnginePreeditAdapter adapter = EnginePreeditAdapter.discover(ModernDecoder.class);
        assertNotNull(adapter); assertNotNull(adapter.cachedText);
        adapter.callbacks.get(0).invoke(decoder, 1L);
        engine(adapter.read(decoder)); client(decoder.display());
        assertEquals("ing", bottom('k'));
    }
    @Test public void olderDecoderUsesItsPreeditGetter() throws Exception {
        OldDecoder decoder = new OldDecoder();
        EnginePreeditAdapter adapter = EnginePreeditAdapter.discover(OldDecoder.class);
        assertNotNull(adapter); assertNull(adapter.cachedText);
        adapter.callbacks.get(0).invoke(decoder, 1L);
        engine(adapter.read(decoder));
        assertEquals("uai", bottom('k'));
    }
    @Test public void ambiguousDecoderShapesAreNotHooked() {
        assertNull(EnginePreeditAdapter.discover(AmbiguousDecoder.class));
    }
    @Test public void lastSeparatedSegmentDeterminesInitialWithoutParityRule() {
        engine("abc'g"); assertEquals("uai", bottom('k'));
        engine("abc'ga"); assertEquals("ing/uai", bottom('k'));
        engine("abc'g"); assertEquals("uai", bottom('k'));
        engine("abc'"); assertEquals("ing/uai", bottom('k'));
    }
    @Test public void filteringShowsOnlyFinalsAndKeepsUeAliasUsableAfterN() {
        engine("n");
        assertEquals("", HintEngine.hint("XIAOHE", 'v', state.initial()).top);
        assertEquals("ue", bottom('t'));
        engine("ni");
        assertEquals("zh", HintEngine.hint("XIAOHE", 'v', state.initial()).top);
    }
    @Test public void spaceSeparatedPhraseUsesLastNonemptySegmentWithDisplayPadding() {
        for (String text : new String[]{"ni h", "ni hao h", "ni hao h ", " ni  hao  h  ",
                "ni\u00a0hao\u00a0h\u00a0", "ni hao h\u200b"}) {
            engine(text); client("你好");
            assertEquals(text, "h", state.initial());
            assertEquals("uai", bottom('k'));
            assertEquals("ua", bottom('x'));
        }
        engine("ni hao v "); assertEquals("zh", state.initial());
        engine("ni hao a "); assertEquals("a", state.initial());
        engine("ni hao e "); assertEquals("e", state.initial());
        engine("ni hao o "); assertEquals("o", state.initial());
        engine("ni hao ha "); assertEquals("", state.initial());
        engine("ni hao "); assertEquals("", state.initial());
        engine("ni hao' "); assertEquals("", state.initial());
    }
    @Test public void independentRawParsingIgnoresCorrectedSegmentation() throws Exception {
        ModernDecoder decoder = new ModernDecoder();
        EnginePreeditAdapter adapter = EnginePreeditAdapter.discover(ModernDecoder.class);
        assertEquals(1, adapter.readers.size());
        assertFalse(java.lang.reflect.Modifier.isPublic(adapter.readers.get(0).getModifiers()));
        decoder.obfuscatedCache = "nihaon";
        decoder.formatted = "ni hao n";
        state.engineInitial(HintEngine.pendingEngineInitial(adapter.cached(decoder),
                (CharSequence)adapter.readers.get(0).invoke(decoder), "XIAOHE")); client(null);
        assertEquals("n", state.initial()); assertEquals("ing", bottom('k'));
        assertEquals("", HintEngine.pendingInitial(adapter.cached(decoder), "XIAOHE"));
        state.engineInitial(HintEngine.pendingEngineInitial("nihaog", "ni hao g", "XIAOHE"));
        assertEquals("g", state.initial()); assertEquals("uai", bottom('k'));
        state.engineInitial(HintEngine.pendingEngineInitial("nihaog", "", "XIAOHE"));
        assertEquals("g", state.initial());
    }
    @Test public void directCacheClearIsDetectedWithoutAnyUpdateOrReaderCallback() throws Exception {
        ModernDecoder decoder = new ModernDecoder();
        EnginePreeditAdapter adapter = EnginePreeditAdapter.discover(ModernDecoder.class);
        decoder.obfuscatedCache = "nihaon";
        assertFalse(adapter.cacheWasCleared(decoder));
        java.lang.reflect.Method clear = ModernDecoder.class.getDeclaredMethod("obfuscatedReset");
        assertTrue(adapter.clearNotifications.contains(clear));
        clear.invoke(decoder);
        assertTrue(adapter.cacheWasCleared(decoder));
        engine(null); assertEquals("", state.initial());
        assertEquals("ing/uai", bottom('k'));
    }
    @Test public void recognizedCompoundInitialAndConvertedPrefixRemainUsable() {
        assertEquals("zh", HintEngine.pendingEngineInitial("nihaov", "ni hao zh", "XIAOHE"));
        assertEquals("ch", HintEngine.pendingEngineInitial("nihaoi", "ni hao ch", "XIAOHE"));
        assertEquals("", HintEngine.pendingEngineInitial("nihaou", "ni hao sh", "XIAOHE"));
        assertEquals("n", HintEngine.pendingEngineInitial("nihaon", "你好n", "XIAOHE"));
        assertEquals("", HintEngine.pendingEngineInitial("nihaoni", "ni hao ni", "XIAOHE"));
        assertEquals("n", HintEngine.pendingEngineInitial("n", "你", "XIAOHE"));
        assertEquals("n", HintEngine.pendingEngineInitial("n", " \u0085\u2009", "XIAOHE"));
    }
    @Test public void allUnicodeWhitespaceSeparatesTheInternalFinalSegment() {
        for (int code = 0; code <= Character.MAX_VALUE; code++) {
            char space = (char)code;
            boolean whitespace = Character.isWhitespace(space) || Character.isSpaceChar(space)
                    || code == 0x85 || code == 0x180e || code == 0x200b || code == 0x2060 || code == 0xfeff;
            if (!whitespace) continue;
            engine("ni" + space + "hao" + space + "n" + space);
            assertEquals("separator U+" + Integer.toHexString(code), "n", state.initial());
            assertEquals("ing", bottom('k'));
        }
    }

    @Test public void greedyPairsUseValidityInsteadOfCorrectionOrParity() {
        assertEquals("", HintEngine.pendingEngineInitial("ng", "n g", "XIAOHE"));
        assertEquals("q", HintEngine.pendingEngineInitial("bq", "biu", "XIAOHE"));
        assertEquals("j", HintEngine.pendingEngineInitial("bqj", "bu qi j", "XIAOHE"));
        assertEquals("n", HintEngine.pendingEngineInitial("nin", "nin", "XIAOHE"));
        assertEquals("", HintEngine.pendingEngineInitial("nini", "ni n i", "XIAOHE"));
        assertEquals("", HintEngine.pendingEngineInitial("n;", "n ;", "MS"));
        assertEquals("", HintEngine.pendingEngineInitial("ah", "a h", "XIAOHE"));
        assertEquals("g", HintEngine.pendingEngineInitial("ag", "ang", "XIAOHE"));
        assertEquals("", HintEngine.pendingEngineInitial("", "old n", "XIAOHE"));
    }
    @Test public void allValidSchemePairsConsumeExactlyTwoKeysBeforeNextInitial() {
        for (String scheme : new String[]{"XIAOHE", "Ziranma", "MS", "ABC", "Ziguang", "PinyinJiaJia"})
            for (char first = 'a'; first <= 'z'; first++) {
                String initial = SchemeData.initial(scheme, first);
                if (initial == null) initial = String.valueOf(first);
                for (char second : "abcdefghijklmnopqrstuvwxyz;".toCharArray()) if (HintEngine.canPair(scheme, initial, second)) {
                    String pair = "" + first + second;
                    assertEquals(scheme + pair, "", HintEngine.independentInitial(pair, scheme));
                    assertEquals("n", HintEngine.independentInitial(pair + "n", scheme));
                }
            }
    }
    public static final class ModernDecoder {
        private volatile CharSequence obfuscatedCache;
        private String formatted = "你";
        protected CharSequence obfuscatedGetter() { return formatted; }
        public CharSequence publicCachedGetter() { return obfuscatedCache; }
        public void obfuscatedReset() { obfuscatedCache = null; }
        public void obfuscatedUpdate(long ignored) { obfuscatedCache = "n"; }
        CharSequence display() { return "你"; }
    }
    public static final class OldDecoder {
        private String raw;
        protected CharSequence obfuscatedGetter() { return raw; }
        public void obfuscatedUpdate(long ignored) { raw = "g"; }
    }
    public static final class AmbiguousDecoder {
        private volatile CharSequence a, b;
        public void update(long ignored) { }
    }
}
