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
    private volatile InputMethodService service;
    private Context configurationContext;
    private BroadcastReceiver controlReceiver, unlockReceiver;
    private boolean observerRegistered;
    private SharedPreferences remotePreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener remoteChanges=(p,key)->
            main.post(() -> refreshConfiguration(true,"remote_preferences_changed"));
    private int startupAttempt;
    private boolean configAccessPending, configAccessPersisted;
    private long nextConfigAccessRequest;
    private long diagnosticConfigGeneration;
    private String diagnosticRenderSignature;
    private static final long[] CONFIG_RETRY_DELAYS = {100, 400, 1000, 2000, 4000, 8000};
    private final Runnable configRetry = () -> refreshConfiguration(false,"retry");
    private volatile boolean enabled = true, dynamic = true, visible, pinyinActive, chineseActive, password, showSemicolon;
    private SemicolonKeys semicolonKeys;
    void nativeKeys(NativeKeyAdapter adapter) { semicolonKeys = new SemicolonKeys(adapter); }
    private volatile boolean inlineUppercaseEnabled, inlineReady, inlineArmed, inlineComposing;
    void inlineSupported(boolean supported) { inlineReady = supported; }
    boolean inlineUppercaseConfigured() { return inlineReady && inlineUppercaseEnabled && service != null && visible && !password; }
    boolean inlineUppercaseActive() { return inlineUppercaseConfigured() && chineseActive && inlineComposing; }
    boolean inlineUppercaseArmed() { return inlineUppercaseActive() && inlineArmed; }
    void inlineDecoderObserved(boolean chinese) { if(!primaryModeKnown)chineseActive = chinese; if (!chinese) { inlineArmed = false;inlineComposing=false; } }
    void toggleInlineUppercase() { if (inlineUppercaseActive()) inlineArmed = !inlineArmed; refreshLater(); }
    void cancelInlineUppercase() { inlineArmed = false; refreshLater(); }
    private int size = 10;
    private volatile boolean flexibleEmoji;
    Context context() { return service != null ? service : configurationContext; }
    boolean emojiAllowed() { return flexibleEmoji && chineseActive && visible && !password; }
    String emojiScheme() { return pinyinActive ? automatic : null; }
    private boolean blankEnabled, initialsOnly, singleVowels, hideCompoundInitials, hideNumberHints;
    private boolean stateOptionsConfigured, defaultLetters = true, finalsLetters = true, initialsBelow;
    private boolean defaultVisibilityConfigured;
    private int defaultVisibility = StateOptions.DEFAULT_ALL;
    private boolean keyLetterSizeEnabled;
    private int keyLetterSize = PrimaryLetterSize.DEFAULT_SP;
    private volatile String automatic;
    private volatile boolean primaryModeKnown, primarySchemeKnown;
    private final DynamicHintState compositionState = new DynamicHintState();
    private final java.util.concurrent.atomic.AtomicLong engineUpdates = new java.util.concurrent.atomic.AtomicLong();
    private boolean scheduled;
    private final Runnable renderTask = () -> { scheduled = false; render(); };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean self) { refreshConfiguration(true,"preferences_changed"); }
    };
    void initialize(Context context) {
        if(configurationContext!=null)return;
        StartupDiagnostics.context("controller.initialize",context);
        StartupDiagnostics.providerVisibility(context);
        Context app=context.getApplicationContext();
        configurationContext=app!=null && HintsModule.TARGET.equals(app.getPackageName()) ? app : context;
        Context ime=configurationContext;
        if (Build.VERSION.SDK_INT >= 24) {
            unlockReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { refreshConfiguration(true,"user_unlocked"); }
            };
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
                if (Build.VERSION.SDK_INT >= 33) ime.registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                else ime.registerReceiver(unlockReceiver, filter);
            } catch (RuntimeException e) { unlockReceiver = null; HintsModule.logFailure("unlock observer", e); }
        }
        // Register unlock before reading: unlocking between the first query and
        // receiver registration must not leave a migrated configuration unread.
        refreshConfiguration(true,"application_initialize");
    }
    void attach(InputMethodService ime) {
        if (service == ime) return;
        if (service != null) detach();
        service = ime;
        StartupDiagnostics.context("ime.attach",ime);
        initialize(ime);
        try { controlReceiver = GboardControl.register(ime, () -> android.os.Process.killProcess(android.os.Process.myPid()),
                () -> KeyboardTest.switchToPinyin(ime)); }
        catch (RuntimeException e) { HintsModule.logFailure("restart receiver", e); }
        // This exact key is also observed when Gboard reads per-profile preferences.
        android.content.SharedPreferences preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(ime);
        if (preferences.contains("pinyin_scheme")) schemeRead(preferences.getString("pinyin_scheme", ""));
    }
    void detach() {
        StartupDiagnostics.record("ime.detach","controller",System.identityHashCode(this));
        if (service != null && controlReceiver != null) service.unregisterReceiver(controlReceiver);
        controlReceiver = null;
        if(remotePreferences!=null)remotePreferences.unregisterOnSharedPreferenceChangeListener(remoteChanges);
        else if (configurationContext != null && observerRegistered)
            configurationContext.getContentResolver().unregisterContentObserver(observer);
        remotePreferences=null;
        if (configurationContext != null && unlockReceiver != null) configurationContext.unregisterReceiver(unlockReceiver);
        observerRegistered = false; unlockReceiver = null;configurationContext=null;
        main.removeCallbacks(configRetry);
        engineUpdates.incrementAndGet();
        if (semicolonKeys != null) semicolonKeys.restore();
        inlineArmed=false;inlineComposing=false;primaryModeKnown=false;primarySchemeKnown=false;
        service = null; visible = false; pinyinActive = false; chineseActive = false; compositionState.reset();
        render();
        for (KeyState key : keys.values()) { key.labels.restore(); key.letterSize.restore(); }
        main.removeCallbacks(renderTask); scheduled = false;
    }
    void start(EditorInfo info) {
        StartupDiagnostics.record("ime.input_view.start","controller",System.identityHashCode(this));
        engineUpdates.incrementAndGet();
        inlineArmed = false;inlineComposing=false; refreshConfiguration(true,"input_view_start"); visible = true; compositionState.reset();
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
    void finish() { StartupDiagnostics.record("ime.input_view.finish");inlineArmed = false;inlineComposing=false; engineUpdates.incrementAndGet(); visible = false; compositionState.reset(); refreshLater(); }
    void imeActivated() {
        primaryModeKnown=false;primarySchemeKnown=false;
        inlineArmed = false;inlineComposing=false;
        long revision = engineUpdates.incrementAndGet();
        Runnable reset = () -> { if(!primaryModeKnown && revision == engineUpdates.get()) {
            pinyinActive = false; chineseActive = false;compositionState.reset(); } refreshLater(); };
        if (Looper.myLooper() == main.getLooper()) reset.run(); else main.post(reset);
    }
    void decoderStarted(boolean pinyin) { decoderStarted(pinyin,pinyin); }
    void decoderStarted(boolean pinyin, boolean chinese) {
        if(primaryModeKnown)return;
        long revision = engineUpdates.incrementAndGet();
        main.post(() -> { if(primaryModeKnown)return; pinyinActive = pinyin; chineseActive = chinese;
            if (revision == engineUpdates.get()) compositionState.reset(); refreshLater(); });
    }
    void primaryDecoderObserved(boolean pinyin, boolean chinese, String nativeScheme) {
        if(!primaryModeKnown || pinyinActive!=pinyin || chineseActive!=chinese)engineUpdates.incrementAndGet();
        primaryModeKnown=true;pinyinActive=pinyin;chineseActive=chinese;
        if(pinyin && nativeScheme!=null){primarySchemeKnown=true;automatic=HintEngine.scheme(nativeScheme);}
        if(!chinese){inlineArmed=false;inlineComposing=false;}
        StartupDiagnostics.changed("decoder.primary",pinyin+":"+chinese+":"+primarySchemeKnown+":"+automatic,"pinyin",pinyin,"Chinese",chinese,"schemeKnown",primarySchemeKnown,"mappedScheme",automatic);
        HintsModule.event("primary mode; pinyin="+pinyin+", Chinese="+chinese+", schemeKnown="+primarySchemeKnown);
        refreshLater();
    }
    void subtypeChanged(InputMethodSubtype subtype) {
        inlineArmed = false;inlineComposing=false;
        engineUpdates.incrementAndGet(); compositionState.reset(); refreshLater();
    }
    void schemeRead(String value) {
        final String scheme = HintEngine.scheme(value);
        StartupDiagnostics.changed("scheme.shared_preferences",value==null?"<null>":value,"value",value,"mapped",scheme,"ignoredBecausePrimaryKnown",primarySchemeKnown);
        main.post(() -> { if (!primarySchemeKnown && !Objects.equals(automatic, scheme)) { automatic = scheme; refreshLater(); } });
    }
    void composition(CharSequence text) {
        // Retain only a possible initial, never the composition or committed text.
        final String next = HintEngine.independentInitial(text,
                automatic);
        final boolean composing = hasPreedit(text);
        main.post(() -> { compositionState.clientInitial(next,composing);inlineComposing=compositionState.composing(); if(!inlineComposing)inlineArmed=false; refreshLater(); });
    }
    void engineComposition(CharSequence text) {
        final String next = HintEngine.independentInitial(text,
                automatic);
        applyEngineInitial(next, hasPreedit(text));
    }
    String engineComposition(CharSequence raw, CharSequence segmented) {
        String next = HintEngine.pendingEngineInitial(raw, segmented,
                automatic);
        applyEngineInitial(next, hasPreedit(raw != null ? raw : segmented));
        return next;
    }
    private static boolean hasPreedit(CharSequence text) {
        if (text != null) for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != '\u0085') return true;
        }
        return false;
    }
    private void applyEngineInitial(String next, boolean composing) {
        inlineComposing=composing;if(!composing)inlineArmed=false;
        long revision = engineUpdates.incrementAndGet();
        Runnable update = () -> {
            if (revision != engineUpdates.get()) return;
            compositionState.engineInitial(next,composing); if(!composing)inlineArmed=false; refreshLater();
        };
        if (Looper.myLooper() == main.getLooper()) update.run(); else main.post(update);
    }
    void recoverKeys(Class<?> keyType, Field metadata) {
        main.post(() -> {
            if(service!=null && service.getWindow()!=null && service.getWindow().getWindow()!=null)
                observeTree(service.getWindow().getWindow().getDecorView(),keyType,metadata);
        });
    }
    void observeTree(View root, Class<?> keyType, Field metadata) {
        if(keyType.isInstance(root))observe(root,metadata);
        if(root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)observeTree(group.getChildAt(i),keyType,metadata);
        }
    }
    void observe(View view, Field metadata) {
        if (keys.containsKey(view)) return;
        keys.put(view, new KeyState(metadata));
        android.view.ViewTreeObserver.OnPreDrawListener preDraw = () -> {
            KeyState state = keys.get(view);
            if (scheduled || state != null && state.shown != view.isShown()) {
                main.removeCallbacks(renderTask); scheduled = false; render();
            }
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
    private void refreshConfiguration(boolean reset,String reason) {
        if (configurationContext == null) {StartupDiagnostics.record("config.skipped","reason",reason,"status","no_context");return;}
        StartupDiagnostics.record("config.request","controller",System.identityHashCode(this),"reason",reason,
                "attempt",startupAttempt,"observerRegistered",observerRegistered);
        if (reset) { startupAttempt = 0; main.removeCallbacks(configRetry); }
        if (!observerRegistered) try {
            if(io.github.rikumi.gboardhints.bridge.XposedBridge.modern!=null
                    && io.github.rikumi.gboardhints.bridge.XposedBridge.modern.supportsRemotePreferences()) {
                remotePreferences=io.github.rikumi.gboardhints.bridge.XposedBridge.modern.getRemotePreferences("config");
                remotePreferences.registerOnSharedPreferenceChangeListener(remoteChanges);
            } else configurationContext.getContentResolver().registerContentObserver(ConfigProvider.URI, false, observer);
            observerRegistered = true;
        } catch (RuntimeException e) { HintsModule.logFailure("preferences observer", e); }
        boolean ready = loadConfig();
        diagnosticConfiguration(ready);
        StartupDiagnostics.record("config.result","controller",System.identityHashCode(this),"success",ready,"observerRegistered",observerRegistered);
        refreshLater();
        main.removeCallbacks(configRetry);
        if (ready && !configAccessPersisted && remotePreferences==null) {
            // Request once even if already visible, so visibility survives a cold boot.
            requestConfigAccess();
        }
        if (!ready || !observerRegistered) {
            if(remotePreferences==null)requestConfigAccess();
            long delay=startupAttempt < CONFIG_RETRY_DELAYS.length ? CONFIG_RETRY_DELAYS[startupAttempt++] : 30000;
            StartupDiagnostics.record("config.retry.scheduled","delayMs",delay,"attempt",startupAttempt);
            main.postDelayed(configRetry,delay);
        }
    }
    private void requestConfigAccess() {
        long now = SystemClock.elapsedRealtime();
        if (configAccessPending || now < nextConfigAccessRequest) return;
        configAccessPending = true;
        nextConfigAccessRequest = now + 10000;
        Intent request = new Intent(ConfigAccessReceiver.ACTION)
                .setComponent(new ComponentName(ConfigAccessReceiver.PACKAGE, ConfigAccessReceiver.class.getName()))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        StartupDiagnostics.record("config.access.request");
        try {
            configurationContext.sendOrderedBroadcast(request,null,new BroadcastReceiver() {
                @Override public void onReceive(Context context,Intent intent) {
                    configAccessPending = false;
                    boolean granted = getResultCode() == android.app.Activity.RESULT_OK;
                    StartupDiagnostics.record("config.access.result","granted",granted);
                    if (granted && configurationContext != null) {
                        ConfigAccessReceiver.persist(configurationContext);
                        configAccessPersisted = true;
                        refreshConfiguration(true,"access_granted");
                    }
                }
            },main,android.app.Activity.RESULT_CANCELED,null,null);
        } catch (RuntimeException e) {
            configAccessPending = false;
            HintsModule.logFailure("request config access",e);
        }
    }
    void enforceKeyState(View view) {
        KeyState state = keys.get(view);
        if (state != null) {
            state.letterSize.apply(view, keyLetterSizeEnabled && visible && view.isShown(), keyLetterSize);
            state.labels.enforceHidden();
        }
    }
    private Cursor configurationCursor() {
        if(remotePreferences!=null) {
            boolean ready=remotePreferences.getBoolean("_snapshot_ready",false);
            StartupDiagnostics.record("config.transport","source","framework_remote_preferences","ready",ready);
            return ready ? ConfigProvider.snapshot(remotePreferences) : null;
        }
        StartupDiagnostics.record("config.transport","source","content_provider");
        return configurationContext.getContentResolver().query(ConfigProvider.URI,null,null,null,null);
    }
    private boolean loadConfig() {
        if (configurationContext == null) return false;
        try (Cursor cursor = configurationCursor()) {
            if (cursor != null && cursor.moveToFirst()) {
                StartupDiagnostics.cursor(cursor);
                if(BuildConfig.DIAGNOSTICS)diagnosticConfigGeneration++;
                HintsModule.event("module configuration read; columns="+cursor.getColumnCount());
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
                int semicolon = cursor.getColumnIndex("show_semicolon");
                showSemicolon = semicolon >= 0 && cursor.getInt(semicolon) != 0;
                inlineUppercaseEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("inline_uppercase")) != 0;
                if (!inlineUppercaseEnabled) inlineArmed = false;
                int emoji = cursor.getColumnIndex("flexible_emoji");
                flexibleEmoji = emoji >= 0 && cursor.getInt(emoji) != 0;
                if(flexibleEmoji) EmojiLexicon.request(configurationContext,emojiScheme(),null);
                int defaultsMode = cursor.getColumnIndex("default_visibility");
                defaultVisibilityConfigured = defaultsMode >= 0;
                defaultVisibility = defaultsMode < 0 ? StateOptions.DEFAULT_ALL : Math.max(StateOptions.DEFAULT_ALL,
                        Math.min(StateOptions.DEFAULT_HIDDEN, cursor.getInt(defaultsMode)));
                int fontEnabled = cursor.getColumnIndex("key_letter_size_enabled");
                int fontSize = cursor.getColumnIndex("key_letter_size");
                keyLetterSizeEnabled = fontEnabled >= 0 && cursor.getInt(fontEnabled) != 0;
                keyLetterSize = fontSize < 0 ? PrimaryLetterSize.DEFAULT_SP : Math.max(PrimaryLetterSize.MIN_SP,
                        Math.min(PrimaryLetterSize.MAX_SP, cursor.getInt(fontSize)));
                if (cursor.getColumnCount() > 10) try { GboardLauncherIcon.setHidden(configurationContext, cursor.getInt(10) != 0); }
                catch (RuntimeException e) { HintsModule.logFailure("Gboard launcher visibility", e); return false; }
                return true;
            }
            StartupDiagnostics.record("config.unavailable","status",cursor==null ? "null_cursor" : "empty_cursor");
        } catch (RuntimeException e) { HintsModule.logFailure("preferences", e); }
        return false;
    }
    private void diagnosticConfiguration(boolean refreshSuccessful) {
        if(!BuildConfig.DIAGNOSTICS)return;
        StartupDiagnostics.record("config.effective","controller",System.identityHashCode(this),"refreshSuccessful",refreshSuccessful,"generation",diagnosticConfigGeneration,
                "enabled",enabled,"hintSizeSp",size,"dynamic",dynamic,"blankEnabled",blankEnabled,
                "initialsOnly",initialsOnly,"hideCompoundInitials",hideCompoundInitials,"singleVowels",singleVowels,
                "hideNumberHints",hideNumberHints,"inlineUppercaseEnabled",inlineUppercaseEnabled,"inlineSupported",inlineReady,
                "showSemicolon",showSemicolon,"flexibleEmoji",flexibleEmoji,"defaultLetters",defaultLetters,"finalsLetters",finalsLetters,
                "initialsBelow",initialsBelow,"defaultVisibility",defaultVisibility,"stateOptionsConfigured",stateOptionsConfigured,
                "defaultVisibilityConfigured",defaultVisibilityConfigured,"keyLetterSizeEnabled",keyLetterSizeEnabled,"keyLetterSizeSp",keyLetterSize,
                "automaticScheme",automatic,"primaryModeKnown",primaryModeKnown,"primarySchemeKnown",primarySchemeKnown);
    }
    private void render() {
        String scheme = automatic;
        if (semicolonKeys != null) semicolonKeys.update(new ArrayList<>(keys.keySet()), showSemicolon && visible && !password,
                chineseActive && visible && !password, pinyinActive, compositionState.composing(), scheme, inlineUppercaseActive());
        ArrayList<Map.Entry<View, KeyState>> snapshot = new ArrayList<>(keys.entrySet());
        Map<View, Character> labels = new IdentityHashMap<>();
        Set<Character> alphabet = new HashSet<>();
        for (Map.Entry<View, KeyState> entry : snapshot) {
            View view = entry.getKey();
            if (view == null) continue;
            entry.getValue().shown = view.isShown();
            entry.getValue().letterSize.apply(view, keyLetterSizeEnabled && visible && entry.getValue().shown, keyLetterSize);
            if (!entry.getValue().shown) continue;
            char key = AdaptiveKeyAdapter.key(view, entry.getValue().metadata);
            labels.put(view, key);
            if (key >= 'a' && key <= 'z') alphabet.add(key);
        }
        boolean alphabetPage = alphabet.size() >= 20;
        boolean shuangpin = visible && alphabetPage && !password && pinyinActive && automatic != null;
        boolean show = enabled && shuangpin && scheme != null;
        if(BuildConfig.DIAGNOSTICS) {
            String signature=diagnosticConfigGeneration+":"+visible+":"+password+":"+pinyinActive+":"+chineseActive+":"+automatic+":"+primaryModeKnown+":"+alphabetPage;
            if(!signature.equals(diagnosticRenderSignature)) {
                diagnosticRenderSignature=signature;
                StartupDiagnostics.record("keyboard.render.state","controller",System.identityHashCode(this),"generation",diagnosticConfigGeneration,
                        "observedKeys",keys.size(),"visibleKeyViews",labels.size(),"alphabetCount",alphabet.size(),"alphabetPage",alphabetPage,
                        "visible",visible,"passwordEditor",password,"pinyinActive",pinyinActive,"chineseActive",chineseActive,"automaticScheme",automatic,
                        "primaryModeKnown",primaryModeKnown,"primarySchemeKnown",primarySchemeKnown,"shuangpinEligible",shuangpin,"hintsAllowed",show);
            }
        }
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
        boolean added, shown;
        KeyState(Field metadata) { this.metadata = metadata; }
    }
}
