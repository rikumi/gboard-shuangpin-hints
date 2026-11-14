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
    @Override public boolean onCreate() { StartupDiagnostics.context("provider.create",getContext());return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        android.content.SharedPreferences p = ConfigStore.prefs(getContext());
        StartupDiagnostics.stored(getContext(),p);
        return snapshot(p);
    }
    static MatrixCursor snapshot(android.content.SharedPreferences p) {
        MatrixCursor result = new MatrixCursor(new String[]{"enabled", "scheme", "size", "dynamic", "blank", "initials_only", "single_vowels", "hide_compound_initials", "hide_number_hints", "inline_uppercase", "hide_gboard_launcher", "default_letters", "finals_letters", "initials_below", "key_letter_size_enabled", "key_letter_size", "default_visibility", "show_semicolon", "flexible_emoji"});
        result.addRow(new Object[]{p.getBoolean("enabled", true) ? 1 : 0,
                "auto", p.getBoolean("hint_size_enabled",false) ? p.getInt("size",10) : 10, p.getBoolean("dynamic", true) ? 1 : 0,
                p.getBoolean("blank_keys", p.getInt("blank", 0) != 0) ? 1 : 0,
                StateOptions.initialsOnly(p) ? 1 : 0, StateOptions.singleVowels(p) ? 1 : 0,
                StateOptions.hideInitials(p) ? 1 : 0, p.getBoolean("hide_number_hints", false) ? 1 : 0, p.getBoolean("inline_uppercase",false) ? 1 : 0, p.getBoolean("hide_gboard_launcher", false) ? 1 : 0,
                StateOptions.defaultLetters(p) ? 1 : 0, StateOptions.finalsLetters(p) ? 1 : 0, StateOptions.initialsBelow(p) ? 1 : 0,
                p.getBoolean("key_letter_size_enabled", false) ? 1 : 0, p.getInt("key_letter_size", PrimaryLetterSize.DEFAULT_SP), StateOptions.defaultVisibility(p), p.getBoolean("show_semicolon", false) ? 1 : 0, p.getBoolean("flexible_emoji", false) ? 1 : 0});
        return result;
    }
    @Override public android.content.res.AssetFileDescriptor openAssetFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        java.util.List<String> path=uri.getPathSegments();
        if (!EmojiLexicon.AUTHORITY.equals(uri.getAuthority()) || !"r".equals(mode) || path.size()!=2 || !"emoji".equals(path.get(0)))
            throw new java.io.FileNotFoundException("Unknown dictionary");
        String profile=path.get(1);
        if (!profile.equals(EmojiLexicon.profile(profile))) throw new java.io.FileNotFoundException("Unknown profile");
        android.content.Context storage=getContext();
        if (android.os.Build.VERSION.SDK_INT>=24) storage=storage.createDeviceProtectedStorageContext();
        String revision;
        try (java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(
                getContext().getAssets().open("emoji/revision.txt"),"UTF-8"))) {
            revision=reader.readLine();
        } catch(java.io.IOException e) { throw new java.io.FileNotFoundException("Dictionary revision unavailable"); }
        if (revision==null || !revision.matches("[0-9a-f]{64}"))
            throw new java.io.FileNotFoundException("Invalid dictionary revision");
        // Content-addressed caching also refreshes same-version local preview updates.
        java.io.File file=new java.io.File(storage.getCacheDir(),"emoji-"+revision+"-"+profile+".tsv");
        synchronized(ConfigProvider.class) {
            if (!file.isFile() || file.length()==0) {
                java.io.File temporary=new java.io.File(file.getPath()+".tmp");
                try (java.io.InputStream input=getContext().getAssets().open("emoji/"+profile+".tsv");
                     java.io.OutputStream output=new java.io.FileOutputStream(temporary)) {
                    byte[] buffer=new byte[16384]; int count;
                    while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                } catch(java.io.IOException e) { temporary.delete(); throw new java.io.FileNotFoundException("Dictionary unavailable"); }
                if (!temporary.renameTo(file)) { temporary.delete(); throw new java.io.FileNotFoundException("Dictionary cache unavailable"); }
            }
        }
        return new android.content.res.AssetFileDescriptor(android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY),0,file.length());
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.gboardhints.preferences"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
