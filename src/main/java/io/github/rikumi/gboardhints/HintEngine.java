/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import java.util.*;

/** Pure hint policy. Never changes text delivered to Gboard or the editor. */
public final class HintEngine {
    private HintEngine() { }
    public static String scheme(String value) {
        if (value == null) return null;
        switch (value.toLowerCase(Locale.ROOT)) {
            case "shuangpin_flypy": case "xiaohe": return "XIAOHE";
            case "shuangpin_ziranma": case "ziranma": return "Ziranma";
            case "shuangpin_ms": case "ms": return "MS";
            case "shuangpin_abc": case "abc": return "ABC";
            case "shuangpin_ziguang": case "ziguang": return "Ziguang";
            case "shuangpin_jiajia": case "pinyinjiajia": return "PinyinJiaJia";
            default: return null;
        }
    }
    public static String pendingInitial(CharSequence text) {
        return pendingInitial(text, null);
    }
    public static String pendingInitial(CharSequence text, String scheme) {
        if (text == null || text.length() > 512) return "";
        String s = text.toString().toLowerCase(Locale.ROOT);
        // Gboard separates recognized segments with spaces and can retain a
        // trailing layout space. Ignore that padding, not a typed apostrophe.
        int end = s.length();
        while (end > 0 && isPreeditSpace(s.charAt(end - 1))) end--;
        int start = 0;
        for (int i = 0; i < end; i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '\u2019' || c == '\u00b7' || isPreeditSpace(c)) start = i + 1;
        }
        if (end - start != 1) return "";
        char key = s.charAt(start);
        if (key < 'a' || key > 'z') return "";
        String mapped = SchemeData.initial(scheme, key);
        return mapped == null ? String.valueOf(key) : mapped;
    }
    public static String pendingEngineInitial(CharSequence raw, CharSequence segmented, String scheme) {
        return independentInitial(raw != null ? raw : segmented, scheme);
    }
    public static String independentInitial(CharSequence text, String scheme) {
        if (text == null || text.length() > 4096) return "";
        String pending = "";
        for (int i = 0; i < text.length(); i++) {
            char key = text.charAt(i);
            // Decoder formatting boundaries must not override our own pairs.
            if (isPreeditSpace(key) || key == '\'' || key == '\u2019' || key == '\u00b7') continue;
            if (key >= 'A' && key <= 'Z') { pending = ""; continue; }
            if (key < 'a' || key > 'z') {
                if (key == ';' && !pending.isEmpty() && canPair(scheme, pending, key)) pending = "";
                else pending = "";
                continue;
            }
            if (!pending.isEmpty() && canPair(scheme, pending, key)) pending = "";
            else {
                String special = SchemeData.initial(scheme, key);
                pending = special == null ? String.valueOf(key) : special;
            }
        }
        return pending;
    }
    static boolean canPair(String scheme, String initial, char key) {
        List<String> values = SchemeData.finals(scheme).get(key);
        if (initial.equals("a") || initial.equals("e") || initial.equals("o")) {
            if (SchemeData.SYLLABLES.contains(initial + key)) return true;
            if (values != null) for (String value : values)
                if (value.length() > 2 && value.startsWith(initial) && SchemeData.SYLLABLES.contains(value)) return true;
            return false;
        }
        if (values != null) for (String value : values) {
            String syllable = initial + value;
            if (value.startsWith("v") && (initial.equals("j") || initial.equals("q") || initial.equals("x") || initial.equals("y")))
                syllable = initial + value.replace('v', 'u');
            if (SchemeData.SYLLABLES.contains(syllable)) return true;
        }
        return false;
    }
    private static boolean isPreeditSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c)
                || c == '\u0085' || c == '\u180e' || c == '\u200b'
                || c == '\u2060' || c == '\ufeff';
    }
    public static String label(String scheme, char key, String initial) {
        KeyHint hint = hint(scheme, key, initial);
        return hint.top.isEmpty() ? hint.bottom : hint.bottom.isEmpty() ? hint.top : hint.top + "/" + hint.bottom;
    }
    public static final class KeyHint {
        static final KeyHint EMPTY = new KeyHint("", "");
        public final String top, center, bottom;
        KeyHint(String top, String bottom) { this(top, "", bottom); }
        KeyHint(String top, String center, String bottom) { this.top = top; this.center = center; this.bottom = bottom; }
        boolean isEmpty() { return top.isEmpty() && center.isEmpty() && bottom.isEmpty(); }
    }
    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) { if (text.length() > 0) text.append('/'); text.append(value); }
        return text.toString();
    }
    public static KeyHint hint(String scheme, char key, String initial) {
        return hint(scheme, key, initial, false, false);
    }
    public static KeyHint hint(String scheme, char key, String initial, boolean initialsOnly, boolean singleVowels) {
        String prefix = initial == null ? "" : initial;
        if (initialsOnly && prefix.isEmpty()) {
            String special = SchemeData.initial(scheme, key);
            return new KeyHint("", special == null ? "" : special);
        }
        // A lone zero-initial vowel is already the start of the final, not
        // a consonant to prepend. Two-letter finals use their literal next
        // letter in the center; longer finals retain the scheme's bottom hint.
        if (prefix.equals("a") || prefix.equals("o") || prefix.equals("e")) {
            return zeroInitialHint(scheme, key, prefix, singleVowels);
        }
        Map<Character, List<String>> finals = SchemeData.finals(scheme);
        List<String> values = finals.get(Character.toLowerCase(key));
        if (values == null) return KeyHint.EMPTY;
        List<String> shown = new ArrayList<>();
        List<String> singles = new ArrayList<>();
        for (String value : values) {
            boolean single = value.length() == 1 && "aeoiuv".indexOf(value.charAt(0)) >= 0;
            if (single && (!singleVowels || prefix.isEmpty())) continue;
            String syllable = prefix + value;
            // ü is written u after j/q/x/y. The engine stores it as v.
            if (value.startsWith("v") && "j q x y".contains(prefix) && !prefix.isEmpty())
                syllable = prefix + "u" + value.substring(1);
            if (prefix.isEmpty() || SchemeData.SYLLABLES.contains(syllable)) {
                String display = value.equals("ve") && values.contains("ue") ? "ue"
                        : value.startsWith("v") && !prefix.isEmpty() && "j q x y".contains(prefix)
                        ? value.replace('v', 'u') : value.replace('v', 'ü');
                List<String> destination = single ? singles : shown;
                if (!destination.contains(display)) destination.add(display);
            }
        }
        StringBuilder label = new StringBuilder();
        for (String value : shown) {
            if (label.length() != 0) label.append('/');
            label.append(value);
        }
        String special = prefix.isEmpty() ? SchemeData.initial(scheme, key) : null;
        return new KeyHint(special == null ? "" : special, join(singles), label.toString());
    }
    public static KeyHint hint(String scheme, char key, String initial, boolean initialsOnly,
                               boolean singleVowels, boolean hideCompoundInitials) {
        KeyHint hint = hint(scheme, key, initial, initialsOnly, singleVowels);
        if (!hideCompoundInitials) return hint;
        return new KeyHint(isCompoundInitial(hint.top) ? "" : hint.top,
                hint.center, isCompoundInitial(hint.bottom) ? "" : hint.bottom);
    }
    public static KeyHint hint(String scheme, char key, String initial, boolean initialsOnly,
                               boolean singleVowels, boolean hideCompoundInitials, boolean initialsBelow) {
        KeyHint result = hint(scheme, key, initial, initialsOnly, singleVowels, hideCompoundInitials);
        if (hideCompoundInitials || initial != null && !initial.isEmpty()) return result;
        String special = SchemeData.initial(scheme, key);
        if (special == null) return result;
        String finals = initialsOnly ? "" : result.bottom;
        return new KeyHint(initialsBelow ? "" : special, result.center,
                initialsBelow ? special + (finals.isEmpty() ? "" : "/" + finals) : finals);
    }
    private static boolean isCompoundInitial(String text) {
        return text.equals("zh") || text.equals("ch") || text.equals("sh");
    }
    private static KeyHint zeroInitialHint(String scheme, char key, String prefix, boolean singleVowels) {
        key = Character.toLowerCase(key);
        List<String> shown = new ArrayList<>();
        List<String> values = SchemeData.finals(scheme).get(key);
        if (values != null) for (String value : values) {
            if (value.length() > 2 && value.startsWith(prefix)
                    && SchemeData.SYLLABLES.contains(value) && !shown.contains(value)) shown.add(value);
        }
        String next = String.valueOf(key);
        String center = singleVowels && key >= 'a' && key <= 'z'
                && SchemeData.SYLLABLES.contains(prefix + next) ? next : "";
        return new KeyHint("", center, join(shown));
    }

}
