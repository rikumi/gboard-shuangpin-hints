/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.*;
import java.lang.reflect.Field;
import java.util.*;

final class HintController {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<View, KeyState> keys = new WeakHashMap<>();
    private InputMethodService service;
    private BroadcastReceiver controlReceiver, unlockReceiver;
    private boolean observerRegistered;
    private int startupAttempt;
    private static final long[] CONFIG_RETRY_DELAYS = {100, 400, 1000, 2000, 4000, 8000};
    private final Runnable configRetry = () -> refreshConfiguration(false);
    private boolean enabled = true, dynamic = true, visible, pinyinActive, password;
    private int size = 10;
    private boolean blankEnabled, initialsOnly, singleVowels, hideCompoundInitials, hideNumberHints;
    private boolean stateOptionsConfigured, defaultLetters = true, finalsLetters = true, initialsBelow;
    private boolean defaultVisibilityConfigured;
    private int defaultVisibility = StateOptions.DEFAULT_ALL;
    private boolean keyLetterSizeEnabled;
    private int keyLetterSize = PrimaryLetterSize.DEFAULT_SP;
    private volatile String automatic;
    private final DynamicHintState compositionState = new DynamicHintState();
    private final java.util.concurrent.atomic.AtomicLong engineUpdates = new java.util.concurrent.atomic.AtomicLong();
    private boolean scheduled;
    private final Runnable renderTask = () -> { scheduled = false; render(); };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean self) { refreshConfiguration(true); }
    };
    void attach(InputMethodService ime) {
        if (service == ime) return;
        if (service != null) detach();
        service = ime;
        try { controlReceiver = GboardControl.register(ime, () -> android.os.Process.killProcess(android.os.Process.myPid()),
                () -> KeyboardTest.switchToPinyin(ime)); }
        catch (RuntimeException e) { HintsModule.logFailure("restart receiver", e); }
        if (Build.VERSION.SDK_INT >= 24) {
            unlockReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { refreshConfiguration(true); }
            };
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
                if (Build.VERSION.SDK_INT >= 33) ime.registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                else ime.registerReceiver(unlockReceiver, filter);
            } catch (RuntimeException e) { unlockReceiver = null; HintsModule.logFailure("unlock observer", e); }
        }
        // Register unlock before reading: unlocking between the first query and
        // receiver registration must not leave a migrated configuration unread.
        refreshConfiguration(true);
        // This exact key is also observed when Gboard reads per-profile preferences.
        android.content.SharedPreferences preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(ime);
        if (preferences.contains("pinyin_scheme")) schemeRead(preferences.getString("pinyin_scheme", ""));
    }
    void detach() {
        if (service != null && controlReceiver != null) service.unregisterReceiver(controlReceiver);
        controlReceiver = null;
        if (service != null && observerRegistered) service.getContentResolver().unregisterContentObserver(observer);
        if (service != null && unlockReceiver != null) service.unregisterReceiver(unlockReceiver);
        observerRegistered = false; unlockReceiver = null;
        main.removeCallbacks(configRetry);
        engineUpdates.incrementAndGet();
        service = null; visible = false; pinyinActive = false; compositionState.reset();
        render();
        for (KeyState key : keys.values()) { key.labels.restore(); key.letterSize.restore(); }
        main.removeCallbacks(renderTask); scheduled = false;
    }
    void start(EditorInfo info) {
        engineUpdates.incrementAndGet();
        refreshConfiguration(true); visible = true; compositionState.reset();
        password = isPassword(info == null ? 0 : info.inputType);
        refreshLater();
    }
    static boolean isPassword(int type) {
        int kind = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || kind == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    }
    void finish() { engineUpdates.incrementAndGet(); visible = false; compositionState.reset(); refreshLater(); }
    void imeActivated() {
        long revision = engineUpdates.incrementAndGet();
        Runnable reset = () -> { pinyinActive = false;
            if (revision == engineUpdates.get()) compositionState.reset(); refreshLater(); };
        if (Looper.myLooper() == main.getLooper()) reset.run(); else main.post(reset);
    }
    void decoderStarted(boolean pinyin) {
        long revision = engineUpdates.incrementAndGet();
        main.post(() -> { pinyinActive = pinyin;
            if (revision == engineUpdates.get()) compositionState.reset(); refreshLater(); });
    }
    void subtypeChanged(InputMethodSubtype subtype) {
        engineUpdates.incrementAndGet(); compositionState.reset(); refreshLater();
    }
    void schemeRead(String value) {
        final String scheme = HintEngine.scheme(value);
        main.post(() -> { if (!Objects.equals(automatic, scheme)) { automatic = scheme; refreshLater(); } });
    }
    void composition(CharSequence text) {
        // Retain only a possible initial, never the composition or committed text.
        final String next = HintEngine.independentInitial(text,
                automatic);
        main.post(() -> { compositionState.clientInitial(next); refreshLater(); });
    }
    void engineComposition(CharSequence text) {
        final String next = HintEngine.independentInitial(text,
                automatic);
        applyEngineInitial(next);
    }
    String engineComposition(CharSequence raw, CharSequence segmented) {
        String next = HintEngine.pendingEngineInitial(raw, segmented,
                automatic);
        applyEngineInitial(next);
        return next;
    }
    private void applyEngineInitial(String next) {
        long revision = engineUpdates.incrementAndGet();
        Runnable update = () -> {
            if (revision != engineUpdates.get()) return;
            compositionState.engineInitial(next); refreshLater();
        };
        if (Looper.myLooper() == main.getLooper()) update.run(); else main.post(update);
    }
    void observe(View view, Field metadata) {
        if (keys.containsKey(view)) return;
        keys.put(view, new KeyState(metadata));
        android.view.ViewTreeObserver.OnPreDrawListener preDraw = () -> {
            if (scheduled) { main.removeCallbacks(renderTask); scheduled = false; render(); }
            enforceKeyState(view);
            return true;
        };
        if (view.isAttachedToWindow()) view.getViewTreeObserver().addOnPreDrawListener(preDraw);
        view.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> refreshLater());
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(preDraw);
                v.getViewTreeObserver().addOnPreDrawListener(preDraw);
                refreshLater();
            }
            @Override public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(preDraw);
                KeyState state = keys.get(v);
                if (state != null) {
                    state.labels.restore(); state.letterSize.restore();
                    if (state.added) { v.getOverlay().remove(state.hint); state.added = false; }
                }
            }
        });
        refreshLater();
    }
    void refreshLater() {
        if (Looper.myLooper() != main.getLooper()) { main.post(this::refreshLater); return; }
        if (!scheduled) { scheduled = true; main.post(renderTask); }
    }
    private void refreshConfiguration(boolean reset) {
        if (service == null) return;
        if (reset) { startupAttempt = 0; main.removeCallbacks(configRetry); }
        if (!observerRegistered) try {
            service.getContentResolver().registerContentObserver(ConfigProvider.URI, false, observer);
            observerRegistered = true;
        } catch (RuntimeException e) { HintsModule.logFailure("preferences observer", e); }
        boolean ready = loadConfig();
        refreshLater();
        if ((!ready || !observerRegistered) && startupAttempt < CONFIG_RETRY_DELAYS.length)
            main.postDelayed(configRetry, CONFIG_RETRY_DELAYS[startupAttempt++]);
    }
    void enforceKeyState(View view) {
        KeyState state = keys.get(view);
        if (state != null) {
            state.letterSize.apply(view, keyLetterSizeEnabled && visible && view.isShown(), keyLetterSize);
            state.labels.enforceHidden();
        }
    }
    private boolean loadConfig() {
        if (service == null) return false;
        try (Cursor cursor = service.getContentResolver().query(ConfigProvider.URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                enabled = cursor.getInt(0) != 0;
                size = Math.max(7, Math.min(17, cursor.getInt(2))); dynamic = cursor.getInt(3) != 0;
                blankEnabled = cursor.getInt(4) != 0;
                initialsOnly = cursor.getColumnCount() > 5 && cursor.getInt(5) != 0;
                singleVowels = cursor.getColumnCount() > 6 && cursor.getInt(6) != 0;
                hideCompoundInitials = cursor.getColumnCount() > 7 && cursor.getInt(7) != 0;
                hideNumberHints = cursor.getColumnCount() > 8 && cursor.getInt(8) != 0;
                int defaults = cursor.getColumnIndex("default_letters");
                int finals = cursor.getColumnIndex("finals_letters");
                int below = cursor.getColumnIndex("initials_below");
                stateOptionsConfigured = defaults >= 0 && finals >= 0 && below >= 0;
                if (stateOptionsConfigured) {
                    defaultLetters = cursor.getInt(defaults) != 0;
                    finalsLetters = cursor.getInt(finals) != 0;
                    initialsBelow = cursor.getInt(below) != 0;
                }
                int defaultsMode = cursor.getColumnIndex("default_visibility");
                defaultVisibilityConfigured = defaultsMode >= 0;
                defaultVisibility = defaultsMode < 0 ? StateOptions.DEFAULT_ALL : Math.max(StateOptions.DEFAULT_ALL,
                        Math.min(StateOptions.DEFAULT_HIDDEN, cursor.getInt(defaultsMode)));
                int fontEnabled = cursor.getColumnIndex("key_letter_size_enabled");
                int fontSize = cursor.getColumnIndex("key_letter_size");
                keyLetterSizeEnabled = fontEnabled >= 0 && cursor.getInt(fontEnabled) != 0;
                keyLetterSize = fontSize < 0 ? PrimaryLetterSize.DEFAULT_SP : Math.max(PrimaryLetterSize.MIN_SP,
                        Math.min(PrimaryLetterSize.MAX_SP, cursor.getInt(fontSize)));
                if (cursor.getColumnCount() > 10) try { GboardLauncherIcon.setHidden(service, cursor.getInt(10) != 0); }
                catch (RuntimeException e) { HintsModule.logFailure("Gboard launcher visibility", e); return false; }
                return true;
            }
        } catch (RuntimeException e) { HintsModule.logFailure("preferences", e); }
        return false;
    }
    private void render() {
        String scheme = automatic;
        ArrayList<Map.Entry<View, KeyState>> snapshot = new ArrayList<>(keys.entrySet());
        Map<View, Character> labels = new IdentityHashMap<>();
        Set<Character> alphabet = new HashSet<>();
        for (Map.Entry<View, KeyState> entry : snapshot) {
            View view = entry.getKey();
            if (view == null) continue;
            entry.getValue().letterSize.apply(view, keyLetterSizeEnabled && visible && view.isShown(), keyLetterSize);
            if (!view.isShown()) continue;
            char key = AdaptiveKeyAdapter.key(view, entry.getValue().metadata);
            labels.put(view, key);
            if (key >= 'a' && key <= 'z') alphabet.add(key);
        }
        boolean alphabetPage = alphabet.size() >= 20;
        boolean shuangpin = visible && alphabetPage && !password && pinyinActive && automatic != null;
        boolean show = enabled && shuangpin && scheme != null;
        for (Map.Entry<View, KeyState> entry : snapshot) {
            View view = entry.getKey(); KeyState state = entry.getValue();
            if (view == null) continue;
            try {
                char key = labels.containsKey(view) ? labels.get(view) : 0;
                boolean waiting = !compositionState.initial().isEmpty();
                boolean finalsPhase = dynamic && waiting;
                boolean showLetters = stateOptionsConfigured ? (waiting ? finalsLetters :
                        defaultVisibilityConfigured ? defaultVisibility != StateOptions.DEFAULT_HIDDEN : defaultLetters) : !blankEnabled;
                boolean filterPrimary = singleVowels && waiting
                        && (stateOptionsConfigured ? shuangpin : show && finalsPhase);
                boolean hideIdleLetter = !waiting && (defaultVisibilityConfigured
                        ? shuangpin && defaultVisibility == StateOptions.DEFAULT_HIDE_VOWELS
                            && "aeoiuv".indexOf(key) >= 0 && SchemeData.initial(scheme, key) != null
                        : show && initialsOnly && (key == 'u' || key == 'i' || key == 'v'));
                HintEngine.KeyHint policy;
                if (!show || key == 0) policy = HintEngine.KeyHint.EMPTY;
                else if (stateOptionsConfigured) policy = HintEngine.hint(scheme, key,
                        dynamic ? compositionState.initial() : "", initialsOnly && !waiting,
                        singleVowels, hideCompoundInitials, initialsBelow);
                else policy = HintEngine.hint(scheme, key, dynamic ? compositionState.initial() : "",
                        initialsOnly && compositionState.initial().isEmpty(), singleVowels, hideCompoundInitials);
                HintEngine.KeyHint matching = filterPrimary && (!dynamic || !show)
                        ? HintEngine.hint(scheme, key, compositionState.initial(), false, true) : policy;
                state.labels.apply(view, shuangpin && !showLetters || filterPrimary || hideIdleLetter, key,
                        stateOptionsConfigured || !blankEnabled,
                        !hideIdleLetter && filterPrimary && (!stateOptionsConfigured || showLetters) && !matching.center.isEmpty(),
                        hideNumberHints && visible && view.isShown());
                // The center is an eligibility signal, never an overlay glyph.
                // Let Gboard render its own text, font, color and pressed state.
                HintEngine.KeyHint text = new HintEngine.KeyHint(policy.top, policy.bottom);
                if (text.isEmpty()) {
                    if (state.added) { view.getOverlay().remove(state.hint); state.added = false; }
                } else {
                    state.hint.update(view, text, size);
                    if (!state.added) { view.getOverlay().add(state.hint); state.added = true; }
                }
            } catch (RuntimeException e) {
                state.labels.restore(); state.letterSize.restore();
                if (state.added) { view.getOverlay().remove(state.hint); state.added = false; }
                HintsModule.logFailure("key rendering", e);
            }
        }
    }
    private static final class KeyState {
        final Field metadata;
        final AdaptiveKeyAdapter.HintDrawable hint = new AdaptiveKeyAdapter.HintDrawable();
        final BlankKeyLabels labels = new BlankKeyLabels();
        final PrimaryLetterSize letterSize = new PrimaryLetterSize();
        boolean added;
        KeyState(Field metadata) { this.metadata = metadata; }
    }
}
