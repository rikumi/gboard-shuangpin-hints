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
    private volatile boolean chineseActive, inlineUppercaseEnabled, inlineArmed, inlineDecoderKnown;
    private boolean inlineReady;
    private InlineUppercaseAdapter inlineAdapter;
    void inlineAdapter(InlineUppercaseAdapter adapter) { inlineAdapter = adapter; }
    void inlineSupported(boolean ready) { inlineReady = ready; }
    Context context() { return service; }
    boolean inlineUppercaseConfigured() { return inlineReady && inlineUppercaseEnabled && !password && service != null; }
    boolean inlineUppercaseActive() { return inlineUppercaseConfigured() && chineseActive; }
    void inlineDecoderObserved(boolean chinese) {
        inlineDecoderKnown = true; chineseActive = chinese;
        if (!chinese) inlineArmed = false;
        refreshLater();
    }
    boolean inlineUppercaseArmed() { return inlineArmed; }
    synchronized void toggleInlineUppercase() { inlineArmed = !inlineArmed; refreshLater(); }
    void cancelInlineUppercase() { inlineArmed = false; refreshLater(); }

    private boolean blankEnabled, initialsOnly, singleVowels, hideCompoundInitials, hideNumberHints;
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
        try { controlReceiver = GboardControl.register(ime, () -> android.os.Process.killProcess(android.os.Process.myPid())); }
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
        service = null; visible = false; pinyinActive = false; chineseActive = false; inlineArmed = false; compositionState.reset();
        render();
        for (KeyState key : keys.values()) { key.labels.restore(); key.shift.restore(); }
        main.removeCallbacks(renderTask); scheduled = false;
    }
    void start(EditorInfo info) {
        engineUpdates.incrementAndGet();
        refreshConfiguration(true); visible = true; inlineArmed = false; compositionState.reset();
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
    void finish() { engineUpdates.incrementAndGet(); inlineArmed = false; visible = false; compositionState.reset(); refreshLater(); }
    void imeActivated() {
        inlineDecoderKnown = false; chineseActive = false; inlineArmed = false;
        long revision = engineUpdates.incrementAndGet();
        Runnable reset = () -> { pinyinActive = false;
            if (revision == engineUpdates.get()) compositionState.reset(); refreshLater(); };
        if (Looper.myLooper() == main.getLooper()) reset.run(); else main.post(reset);
    }
    void decoderStarted(boolean pinyin) { decoderStarted(pinyin, pinyin); }
    void decoderStarted(boolean pinyin, boolean chinese) {
        if (!inlineDecoderKnown) chineseActive = chinese;
        inlineArmed = false;
        long revision = engineUpdates.incrementAndGet();
        main.post(() -> { pinyinActive = pinyin;
            if (revision == engineUpdates.get()) compositionState.reset(); refreshLater(); });
    }
    void subtypeChanged(InputMethodSubtype subtype) {
        inlineArmed = false; engineUpdates.incrementAndGet(); compositionState.reset(); refreshLater();
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
                    state.labels.restore(); state.shift.restore();
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
        if (state != null) { state.labels.enforceHidden(); state.shift.enforceHidden(); }
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
                inlineUppercaseEnabled = cursor.getColumnCount() > 9 && cursor.getInt(9) != 0;
                if (!inlineUppercaseEnabled) inlineArmed = false;
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
            if (view == null || !view.isShown()) continue;
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
                boolean finalsPhase = dynamic && !compositionState.initial().isEmpty();
                boolean filterPrimary = show && singleVowels && finalsPhase;
                boolean hideIdleLetter = show && initialsOnly && compositionState.initial().isEmpty()
                        && (key == 'u' || key == 'i' || key == 'v');
                HintEngine.KeyHint policy = !show || key == 0 ? HintEngine.KeyHint.EMPTY
                        : HintEngine.hint(scheme, key, dynamic ? compositionState.initial() : "",
                                initialsOnly && compositionState.initial().isEmpty(), singleVowels, hideCompoundInitials);
                state.labels.apply(view, shuangpin && blankEnabled || filterPrimary || hideIdleLetter, key,
                        !blankEnabled, !hideIdleLetter && filterPrimary && !policy.center.isEmpty(),
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
                Object definition = null;
                if (state.metadata != null) {
                    try { definition = state.metadata.get(view); } catch (IllegalAccessException ignored) { }
                }
                state.shift.apply(view, definition, visible && inlineUppercaseActive()
                        && inlineAdapter != null && inlineAdapter.replaceableDefinition(definition), inlineArmed);
            } catch (RuntimeException e) {
                state.labels.restore(); state.shift.restore();
                if (state.added) { view.getOverlay().remove(state.hint); state.added = false; }
                HintsModule.logFailure("key rendering", e);
            }
        }
    }
    private static final class KeyState {
        final Field metadata;
        final AdaptiveKeyAdapter.HintDrawable hint = new AdaptiveKeyAdapter.HintDrawable();
        final BlankKeyLabels labels = new BlankKeyLabels();
        final InlineShiftKey shift = new InlineShiftKey();
        boolean added;
        KeyState(Field metadata) { this.metadata = metadata; }
    }
}
