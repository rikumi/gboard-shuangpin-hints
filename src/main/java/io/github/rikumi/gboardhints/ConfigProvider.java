/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Only display preferences are public; all writes stay inside the module app. */
public final class ConfigProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://io.github.rikumi.gboardhints.config/preferences");
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        android.content.SharedPreferences p = ConfigStore.prefs(getContext());
        MatrixCursor result = new MatrixCursor(new String[]{"enabled", "scheme", "size", "dynamic", "blank", "initials_only", "single_vowels", "hide_compound_initials", "hide_number_hints", "inline_uppercase", "hide_gboard_launcher", "default_letters", "finals_letters", "initials_below", "key_letter_size_enabled", "key_letter_size", "default_visibility"});
        result.addRow(new Object[]{p.getBoolean("enabled", true) ? 1 : 0,
                "auto", p.getInt("size", 10), p.getBoolean("dynamic", true) ? 1 : 0,
                p.getBoolean("blank_keys", p.getInt("blank", 0) != 0) ? 1 : 0,
                StateOptions.initialsOnly(p) ? 1 : 0, StateOptions.singleVowels(p) ? 1 : 0,
                StateOptions.hideInitials(p) ? 1 : 0, p.getBoolean("hide_number_hints", false) ? 1 : 0, 0 /* retired column, retained for older injected modules */, p.getBoolean("hide_gboard_launcher", false) ? 1 : 0,
                StateOptions.defaultLetters(p) ? 1 : 0, StateOptions.finalsLetters(p) ? 1 : 0, StateOptions.initialsBelow(p) ? 1 : 0,
                p.getBoolean("key_letter_size_enabled", false) ? 1 : 0, p.getInt("key_letter_size", PrimaryLetterSize.DEFAULT_SP), StateOptions.defaultVisibility(p)});
        return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.gboardhints.preferences"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
