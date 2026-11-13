/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.SharedPreferences;

/** Reads legacy values without persisting defaults before the user's first unlock. */
final class StateOptions {
    static final int ALL = 0, INITIALS_ONLY = 1, HIDE_INITIALS = 2;
    static final int LETTERS_ALL = 0, LETTERS_AVAILABLE = 1, LETTERS_HIDDEN = 2;
    static final int DEFAULT_ALL = 0, DEFAULT_HIDE_VOWELS = 1, DEFAULT_HIDDEN = 2;
    private StateOptions() { }
    private static boolean blank(SharedPreferences prefs) {
        return prefs.getBoolean("blank_keys", prefs.getInt("blank", 0) != 0);
    }
    static int defaultVisibility(SharedPreferences prefs) {
        int saved = prefs.getInt("default_visibility", -1);
        if (saved >= DEFAULT_ALL && saved <= DEFAULT_HIDDEN) return saved;
        if (!prefs.getBoolean("default_letters", !blank(prefs))) return DEFAULT_HIDDEN;
        return initialsOnly(prefs) ? DEFAULT_HIDE_VOWELS : DEFAULT_ALL;
    }
    static boolean defaultLetters(SharedPreferences prefs) { return defaultVisibility(prefs) != DEFAULT_HIDDEN; }
    private static SharedPreferences.Editor writeDefaultVisibility(SharedPreferences.Editor edit, int mode) {
        return edit.putInt("default_visibility", mode).putBoolean("default_letters", mode != DEFAULT_HIDDEN);
    }
    static void setDefaultVisibility(SharedPreferences prefs, int mode) {
        if (mode < DEFAULT_ALL || mode > DEFAULT_HIDDEN) throw new IllegalArgumentException("Unknown default letter visibility");
        writeDefaultVisibility(prefs.edit(), mode).apply();
    }
    static int finalsVisibility(SharedPreferences prefs) {
        int saved = prefs.getInt("finals_visibility", -1);
        if (saved >= LETTERS_ALL && saved <= LETTERS_HIDDEN) return saved;
        boolean visible = prefs.getBoolean("finals_letters", !blank(prefs) || prefs.getBoolean("single_vowels", false));
        if (!visible) return LETTERS_HIDDEN;
        return prefs.getBoolean("single_vowels", false) ? LETTERS_AVAILABLE : LETTERS_ALL;
    }
    static boolean finalsLetters(SharedPreferences prefs) { return finalsVisibility(prefs) != LETTERS_HIDDEN; }
    static boolean singleVowels(SharedPreferences prefs) { return finalsVisibility(prefs) == LETTERS_AVAILABLE; }
    private static SharedPreferences.Editor writeFinalsVisibility(SharedPreferences.Editor edit, int mode) {
        return edit.putInt("finals_visibility", mode)
                .putBoolean("finals_letters", mode != LETTERS_HIDDEN)
                .putBoolean("single_vowels", mode == LETTERS_AVAILABLE);
    }
    static void setFinalsVisibility(SharedPreferences prefs, int mode) {
        if (mode < LETTERS_ALL || mode > LETTERS_HIDDEN) throw new IllegalArgumentException("Unknown letter visibility");
        writeFinalsVisibility(prefs.edit(), mode).apply();
    }
    static int hintRange(SharedPreferences prefs) {
        int saved = prefs.getInt("hint_range", -1);
        if (saved >= ALL && saved <= HIDE_INITIALS) return saved;
        // Hiding initials takes precedence when both legacy switches were on.
        if (prefs.getBoolean("hide_compound_initials", false)) return HIDE_INITIALS;
        return prefs.getBoolean("initials_only", false) ? INITIALS_ONLY : ALL;
    }
    static boolean initialsOnly(SharedPreferences prefs) { return hintRange(prefs) == INITIALS_ONLY; }
    static boolean hideInitials(SharedPreferences prefs) { return hintRange(prefs) == HIDE_INITIALS; }
    private static boolean allInitialsBelow(SharedPreferences prefs) {
        return prefs.getBoolean("all_initials_below", hintRange(prefs) == ALL && prefs.getBoolean("initials_below", false));
    }
    static boolean initialsBelow(SharedPreferences prefs) {
        return hintRange(prefs) != ALL || allInitialsBelow(prefs);
    }
    private static SharedPreferences.Editor writeRange(SharedPreferences.Editor edit, int range, boolean allBelow) {
        return edit.putInt("hint_range", range)
                .putBoolean("initials_only", range == INITIALS_ONLY)
                .putBoolean("hide_compound_initials", range == HIDE_INITIALS)
                .putBoolean("all_initials_below", allBelow)
                .putBoolean("initials_below", range != ALL || allBelow);
    }
    static void setHintRange(SharedPreferences prefs, int range) {
        if (range < ALL || range > HIDE_INITIALS) throw new IllegalArgumentException("Unknown hint range");
        writeRange(prefs.edit(), range, allInitialsBelow(prefs)).apply();
    }
    static void setAllInitialsBelow(SharedPreferences prefs, boolean below) {
        writeRange(prefs.edit(), hintRange(prefs), below).apply();
    }
    static void migrate(SharedPreferences prefs) {
        SharedPreferences.Editor edit = prefs.edit();
        writeDefaultVisibility(edit, defaultVisibility(prefs));
        writeFinalsVisibility(edit, finalsVisibility(prefs));
        writeRange(edit, hintRange(prefs), allInitialsBelow(prefs)).apply();
    }
}
