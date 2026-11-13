/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline indices generated from the manually reviewed Mainland Chinese dictionary, independent of Gboard correction. */
final class EmojiLexicon {
    static final String AUTHORITY = "io.github.rikumi.gboardhints.config";
    private final Map<String,List<String>> entries;
    private static final Map<String,EmojiLexicon> cache = new LinkedHashMap<>(4,.75f,true);
    private static final Map<String,Set<Runnable>> pending = new HashMap<>();
    private static final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task,"GboardHintsEmoji"); thread.setDaemon(true); return thread;
    });
    EmojiLexicon(Reader source) throws IOException {
        Map<String,List<String>> data = new HashMap<>();
        Map<String,List<String>> initials = new HashMap<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line=reader.readLine()) != null) {
            String[] columns = line.split("\\t", -1);
            if (columns.length == 3) {
                // One reviewed emoji per row; exact spellings outrank abbreviations.
                for (String query : columns[1].split(" ")) add(data, query, columns[0]);
                for (String query : columns[2].split(" ")) add(initials, query, columns[0]);
            } else if (columns.length == 2) {
                for (String emoji : columns[1].split(" ")) add(data, columns[0], emoji);
            }
        }
        for (Map.Entry<String,List<String>> entry : initials.entrySet())
            for (String emoji : entry.getValue()) add(data, entry.getKey(), emoji);
        entries = data;
    }
    private static void add(Map<String,List<String>> index, String query, String emoji) {
        if (query.isEmpty() || emoji.isEmpty()) return;
        List<String> values = index.get(query);
        if (values == null) { values = new ArrayList<>(); index.put(query, values); }
        if (!values.contains(emoji)) values.add(emoji);
    }
    static String profile(String scheme) { return scheme == null || SchemeData.finals(scheme).isEmpty() ? "full" : scheme; }
    static EmojiLexicon request(Context context, String scheme, Runnable ready) {
        String profile = profile(scheme);
        synchronized (cache) {
            EmojiLexicon result = cache.get(profile);
            if (result != null) return result;
            Set<Runnable> callbacks = pending.get(profile);
            if (callbacks != null) { if (ready != null) callbacks.add(ready); return null; }
            callbacks = new HashSet<>(); if (ready != null) callbacks.add(ready); pending.put(profile,callbacks);
        }
        Context app = context.getApplicationContext();
        loader.execute(() -> {
            EmojiLexicon dictionary = null;
            Uri uri = Uri.parse("content://" + AUTHORITY + "/emoji/" + profile);
            try (InputStream input=app.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new FileNotFoundException();
                dictionary = new EmojiLexicon(new InputStreamReader(input,"UTF-8"));
            } catch (IOException | RuntimeException e) { HintsModule.logFailure("emoji dictionary",e); }
            Set<Runnable> callbacks;
            synchronized (cache) {
                callbacks = pending.remove(profile);
                if (dictionary != null) {
                    cache.put(profile,dictionary);
                    while (cache.size() > 2) cache.remove(cache.keySet().iterator().next());
                }
            }
            if (dictionary != null && callbacks != null) for (Runnable callback : callbacks) new Handler(Looper.getMainLooper()).post(callback);
        });
        return null;
    }
    List<String> match(CharSequence raw) {
        List<String> result = entries.get(normalize(raw));
        return result == null ? Collections.emptyList() : result;
    }
    static String normalize(CharSequence text) {
        if (text == null || text.length() > 256) return "";
        StringBuilder query=new StringBuilder();
        for (int i=0;i<text.length();) {
            int c=Character.codePointAt(text,i); i+=Character.charCount(c);
            if (Character.isLetterOrDigit(c) || c==';') query.appendCodePoint(Character.toLowerCase(c));
            else if (c != '\'' && c != 0x2019 && c != 0x85 && !Character.isWhitespace(c) && !Character.isSpaceChar(c)) return "";
        }
        return query.length() > 96 ? "" : query.toString();
    }
}
