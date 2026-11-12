/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

/** SDK-only JVM regression runner: java ... HintEngineTest. */
public final class HintEngineTest {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void yes(boolean value) { equal(true, value); }
    public static void main(String[] args) {
        equal("XIAOHE", HintEngine.scheme("shuangpin_flypy"));
        equal("Ziranma", HintEngine.scheme("shuangpin_ziranma"));
        equal(null, HintEngine.scheme("pinyin"));
        equal(null, HintEngine.scheme("unknown"));
        equal("iao", HintEngine.label("XIAOHE", 'n', ""));
        equal("ao", HintEngine.label("XIAOHE", 'c', ""));
        equal("ing/uai", HintEngine.label("XIAOHE", 'k', ""));
        equal("uai", HintEngine.label("XIAOHE", 'k', "g"));
        equal("ing", HintEngine.label("XIAOHE", 'k', "n"));
        equal("iao", HintEngine.label("XIAOHE", 'n', "x"));
        equal("", HintEngine.label("XIAOHE", 'n', "zh"));
        equal("", HintEngine.label("XIAOHE", 'v', "n"));
        equal("", HintEngine.label("XIAOHE", 'a', ""));
        equal("", HintEngine.label("XIAOHE", 'e', ""));
        equal("uo", HintEngine.label("XIAOHE", 'o', ""));
        equal("ch", HintEngine.label("XIAOHE", 'i', ""));
        equal("sh", HintEngine.label("XIAOHE", 'u', ""));
        equal("zh/ui", HintEngine.label("XIAOHE", 'v', ""));
        equal("zh", HintEngine.hint("XIAOHE", 'v', "").top);
        equal("ui", HintEngine.hint("XIAOHE", 'v', "").bottom);
        equal("ch", HintEngine.hint("XIAOHE", 'i', "").top);
        equal("", HintEngine.hint("XIAOHE", 'i', "").bottom);
        equal("ue", HintEngine.label("XIAOHE", 't', "n"));
        equal("ue", HintEngine.hint("XIAOHE", 't', "").bottom);
        equal("ue", HintEngine.hint("Ziranma", 't', "").bottom);
        equal("üe", HintEngine.hint("MS", 'v', "n").bottom);
        equal("ue", HintEngine.label("XIAOHE", 't', "j"));
        equal("", HintEngine.label("unknown", 'n', ""));
        equal("ing", HintEngine.label("MS", ';', ""));
        equal("ing", HintEngine.label("Ziguang", ';', ""));
        equal("ing/uai", HintEngine.label("Ziranma", 'y', ""));
        equal("ia/ua", HintEngine.label("ABC", 'd', ""));
        equal("iao", HintEngine.label("PinyinJiaJia", 'k', ""));
        equal("n", HintEngine.pendingInitial("n"));
        equal("", HintEngine.pendingInitial("ni'zh"));
        equal("h", HintEngine.pendingInitial("ni h"));
        equal("", HintEngine.pendingInitial("ni"));
        equal("", HintEngine.pendingInitial("你好"));
        equal("", HintEngine.pendingInitial(null));
        equal("a", HintEngine.pendingInitial("a"));
        equal("", HintEngine.pendingInitial("abc"));
        equal("n", HintEngine.pendingInitial("N"));
        equal("zh", HintEngine.pendingInitial("v", "XIAOHE"));
        equal("ch", HintEngine.pendingInitial("i", "XIAOHE"));
        equal("sh", HintEngine.pendingInitial("u", "XIAOHE"));
        equal("zh", HintEngine.pendingInitial("a", "ABC"));
        equal("a", HintEngine.pendingInitial("a", "XIAOHE"));
        equal("zh", HintEngine.pendingInitial("ni'v", "XIAOHE"));
        equal("h", HintEngine.pendingInitial("n'h", "XIAOHE"));
        equal("h", HintEngine.pendingInitial("abc'h", "XIAOHE"));
        equal("", HintEngine.pendingInitial("abc", "XIAOHE"));
        equal("", HintEngine.pendingInitial("ni'", "XIAOHE"));
        equal("", HintEngine.pendingInitial("ni'ha", "XIAOHE"));
        equal("", HintEngine.pendingInitial("zh", "XIAOHE"));
        equal("n", HintEngine.pendingInitial("ni\u2019N", "XIAOHE"));
        for (String scheme : new String[]{"XIAOHE", "Ziranma", "MS", "ABC", "Ziguang", "PinyinJiaJia"}) {
            for (char key : "abcdefghijklmnopqrstuvwxyz;".toCharArray()) {
                yes(HintEngine.label(scheme, key, "").length() < 20);
                for (String initial : new String[]{"n", "g", "zh", "x"}) {
                    String hint = HintEngine.label(scheme, key, initial);
                    yes(hint.length() < 20);
                }
            }
        }
        yes(HintController.isPassword(0x81));
        yes(HintController.isPassword(0x91));
        yes(HintController.isPassword(0xe1));
        yes(HintController.isPassword(0x12));
        equal(false, HintController.isPassword(0x21));
        equal(false, HintController.isPassword(0x02));
        System.out.println("PASS: " + checks + " hint policy checks");
    }
}
