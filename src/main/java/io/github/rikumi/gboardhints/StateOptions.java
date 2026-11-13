/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.SharedPreferences;

/** Reads legacy values without persisting defaults before the user's first unlock. */
final class StateOptions {
    private StateOptions() { }
    private static boolean blank(SharedPreferences prefs) {
        return prefs.getBoolean("blank_keys", prefs.getInt("blank", 0) != 0);
    }
    static boolean defaultLetters(SharedPreferences prefs) {
        return prefs.getBoolean("default_letters", !blank(prefs));
    }
    static boolean finalsLetters(SharedPreferences prefs) {
        // The old single-vowel filter let matching native vowels through blanking.
        return prefs.getBoolean("finals_letters", !blank(prefs) || prefs.getBoolean("single_vowels", false));
    }
    static boolean initialsBelow(SharedPreferences prefs) {
        return prefs.getBoolean("initials_below", prefs.getBoolean("initials_only", false));
    }
    static void migrate(SharedPreferences prefs) {
        SharedPreferences.Editor edit = prefs.edit();
        if (!prefs.contains("default_letters")) edit.putBoolean("default_letters", defaultLetters(prefs));
        if (!prefs.contains("finals_letters")) edit.putBoolean("finals_letters", finalsLetters(prefs));
        if (!prefs.contains("initials_below")) edit.putBoolean("initials_below", initialsBelow(prefs));
        edit.apply();
    }
}
