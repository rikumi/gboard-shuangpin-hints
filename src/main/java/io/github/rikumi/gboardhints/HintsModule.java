/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import java.lang.reflect.*;
import java.util.*;
import io.github.rikumi.gboardhints.bridge.*;

public final class HintsModule  {
    static final String TARGET = "com.google.android.inputmethod.latin";
    private final Set<Method> connectionHooks = new HashSet<>();
    private static final Set<String> reportedFailures = Collections.synchronizedSet(new HashSet<>());
    void start(String packageName,String processName,ClassLoader classLoader) throws Throwable {
        if (!TARGET.equals(packageName)) return;
        StartupDiagnostics.begin(packageName,processName,classLoader);
        try { installPackage(classLoader); }
        catch(Throwable error) {StartupDiagnostics.failure("package.install",error);throw error;}
    }
    private void installPackage(ClassLoader classLoader) throws Throwable {
        HintController controller = new HintController();
        // Capture scheme reads during Application.onCreate without initializing
        // Gboard's own classes before its application globals exist.
        Class<?> preferences=XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl",classLoader);
        if(preferences!=null)hooks.hook(preferences.getDeclaredMethod("getString",String.class,String.class),new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if("pinyin_scheme".equals(p.args[0]) && !p.hasThrowable())controller.schemeRead((String)p.getResult());
            }
        });
        DeferredHooks startup = new DeferredHooks(HintsModule::logFailure);
        startup.add("settings startup", new String[]{"androidx.preference.Preference",
                "androidx.preference.PreferenceGroup", "androidx.preference.ListPreference",
                "androidx.preference.PreferenceScreen",
                "com.google.android.libraries.inputmethod.preferencewidgets.CommonPreferenceFragment"},
                GboardSettingsEntryHooks::install);
        startup.add("keyboard startup", new String[]{AdaptiveKeyAdapter.KEY_CLASS,
                "com.android.inputmethod.latin.LatinIME",
                "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView",
                "com.google.android.libraries.inputmethod.ime.processor.ProcessorBasedIme",
                "com.google.android.libraries.inputmethod.ime.AbstractIme",
                "com.google.android.apps.inputmethod.libs.pinyin.HmmPinyinQwertyDecodeProcessor",
                "com.google.android.apps.inputmethod.libs.hmm.AbstractHmmDecodeProcessor",
                "com.google.android.apps.inputmethod.libs.chinese.ime.hmm.AbstractHmmChineseDecodeProcessor"},
                loader -> installKeyboard(loader,controller));
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        Set<ClassLoader> loaders=Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ClassLoader,Boolean>()));
        loaders.add(classLoader);
        java.util.concurrent.atomic.AtomicBoolean applicationReady=new java.util.concurrent.atomic.AtomicBoolean();
        Context[] applicationContext={null};
        Set<ClassLoader> queued=Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ClassLoader,Boolean>()));
        int[] startupAttempt={0};
        Runnable retry=new Runnable() {
            @Override public void run() {
                if(!applicationReady.get())return;
                StartupDiagnostics.record("hooks.retry","attempt",startupAttempt[0]);
                List<ClassLoader> snapshot;
                synchronized(loaders){snapshot=new ArrayList<>(loaders);}
                for(ClassLoader loader:snapshot)startup.attempt(loader);
                if(startup.pending())main.postDelayed(this,startupAttempt[0]++ < 5 ? 1000 : 30000);
            }
        };
        Runnable activate=() -> {
            Context context=applicationContext[0];
            if(context==null)return;
            StartupDiagnostics.context("application.activate",context);
            startup.applicationReady();applicationReady.set(true);controller.initialize(context);
            loaders.add(context.getClassLoader());
            main.removeCallbacks(retry);retry.run();
        };
        // Framework hooks are safe before Gboard initializes its own globals.
        hooks.hook(InputMethodService.class.getDeclaredMethod("onCreate"),new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                InputMethodService ime=(InputMethodService)p.thisObject;
                if(TARGET.equals(ime.getPackageName()))controller.attach(ime);
            }
        });
        hooks.hook(InputMethodService.class.getDeclaredMethod("getCurrentInputConnection"),new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                InputMethodService ime=(InputMethodService)p.thisObject;
                if(TARGET.equals(ime.getPackageName())) {
                    controller.attach(ime);
                    Object connection=p.getResult();
                    if(connection instanceof InputConnection)watchConnection(connection.getClass(),controller);
                }
            }
        });
        XposedHelpers.findAndHookMethod(android.app.Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Context context=(Context)p.args[0];
                if(!TARGET.equals(context.getPackageName()))return;
                StartupDiagnostics.context("application.attach",context);
                applicationContext[0]=context;loaders.add(context.getClassLoader());
                controller.initialize(context);
                // The queued fallback runs after handleBindApplication/onCreate returns.
                main.removeCallbacks(activate);main.post(activate);
            }
        });
        XposedHelpers.findAndHookMethod(android.app.Instrumentation.class,"callApplicationOnCreate",android.app.Application.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                android.app.Application app=(android.app.Application)p.args[0];
                if(!TARGET.equals(app.getPackageName()))return;
                StartupDiagnostics.context("application.onCreate.complete",app);
                applicationContext[0]=app;main.removeCallbacks(activate);activate.run();
            }
        });
        XposedBridge.hookAllMethods(ClassLoader.class,"loadClass",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if(!applicationReady.get() || p.hasThrowable() || p.args.length==0 || !(p.getResult() instanceof Class))return;
                String name=(String)p.args[0];
                if(!name.equals(AdaptiveKeyAdapter.KEY_CLASS) && !name.startsWith("androidx.preference.")
                        && !name.equals("com.android.inputmethod.latin.LatinIME")
                        && !name.startsWith("com.google.android.libraries.inputmethod.preferencewidgets.")
                        && !name.startsWith("com.google.android.apps.inputmethod.libs.pinyin."))return;
                ClassLoader candidate=((Class<?>)p.getResult()).getClassLoader();
                if(candidate!=null && queued.add(candidate)) {
                    loaders.add(candidate);
                    // Do not resolve other classes while the caller holds a class-loader lock.
                    main.post(() -> {try{startup.attempt(candidate);}finally{queued.remove(candidate);}});
                }
            }
        });
        android.app.Application current=XposedBridge.currentApplication();
        StartupDiagnostics.record("application.current","present",current!=null);
        if(current!=null && TARGET.equals(current.getPackageName())) {
            applicationContext[0]=current;main.post(activate);
        }
    }
    private final HookRegistry hooks = new HookRegistry();
    private InlineUppercaseHooks inlineHooks;
    private EmojiSuggestions emojiHooks;
    private void installKeyboard(ClassLoader loader, HintController controller) throws ReflectiveOperationException {
        Class<?> serviceType = XposedHelpers.findClassIfExists("com.android.inputmethod.latin.LatinIME", loader);
        if (serviceType == null) serviceType = InputMethodService.class;
        Class<?> keys = XposedHelpers.findClassIfExists(AdaptiveKeyAdapter.KEY_CLASS, loader);
        if (keys == null) { XposedBridge.log("GboardHints: unsupported key view; no hooks installed"); return; }
        hooks.hook(android.widget.TextView.class.getDeclaredMethod("setTextSize", int.class, float.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Float size = PrimaryLetterSize.nativeRequest((android.widget.TextView)p.thisObject, (Integer)p.args[0], (Float)p.args[1]);
                if (size != null) { p.args[0] = android.util.TypedValue.COMPLEX_UNIT_SP; p.args[1] = size; }
            }
        });
        Field metadata = AdaptiveKeyAdapter.metadataField(keys);
        if (metadata != null) try { controller.nativeKeys(new NativeKeyAdapter(keys,metadata)); }
        catch (ReflectiveOperationException | RuntimeException e) { logFailure("native semicolon adapter",e); }
        hooks.constructors(keys, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { controller.observe((View)p.thisObject, metadata); }
                catch (RuntimeException e) { logFailure("observe", e); }
            }
        });
        Class<?> keyboard=XposedHelpers.findClassIfExists("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView",loader);
        if(keyboard!=null)try {
            hooks.hook(implementation(keyboard,"onLayout",boolean.class,int.class,int.class,int.class,int.class),new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){controller.observeTree((View)p.thisObject,keys,metadata);controller.refreshLater();}
            });
        } catch(NoSuchMethodException | RuntimeException error){logFailure("native keyboard layout",error);}
        int bindings = 0;
        if (metadata != null) for (Method method : keys.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
            Class<?>[] args = method.getParameterTypes();
            if (args.length > 0 && args[0] == metadata.getType()) {
                hooks.hook(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) { controller.refreshLater(); }
                });
                bindings++;
            }
        }
        try {
            hooks.hook(implementation(keys, "onDraw", android.graphics.Canvas.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { controller.enforceKeyState((View)p.thisObject); }
            });
        } catch (NoSuchMethodException | RuntimeException e) { logFailure("key draw enforcement", e); }
        hooks.hook(implementation(serviceType, "onCreate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.attach((InputMethodService)p.thisObject); }
        });
        hooks.hook(implementation(serviceType, "onStartInputView", EditorInfo.class, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { controller.attach((InputMethodService)p.thisObject); controller.start((EditorInfo)p.args[0]); }
            @Override protected void afterHookedMethod(MethodHookParam p) { if(inlineHooks!=null)inlineHooks.refreshCurrent(); }
        });
        hooks.hook(implementation(serviceType, "onFinishInputView", boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.finish(); }
        });
        hooks.hook(implementation(serviceType, "onFinishInput"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.finish(); }
        });
        hooks.hook(implementation(serviceType, "onUpdateSelection", int.class, int.class, int.class,
                int.class, int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int start = (Integer)p.args[4], end = (Integer)p.args[5];
                if (start < 0 || end < 0 || (Integer)p.args[2] != end || (Integer)p.args[3] != end)
                    controller.composition(null);
            }
        });
        hooks.hook(implementation(serviceType, "onCurrentInputMethodSubtypeChanged", InputMethodSubtype.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { controller.subtypeChanged((InputMethodSubtype)p.args[0]); }
        });
        hooks.hook(implementation(serviceType, "onDestroy"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.detach(); }
        });
        hooks.hook(implementation(serviceType, "getCurrentInputConnection"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object result = p.getResult();
                if (result instanceof InputConnection) watchConnection(result.getClass(), controller);
            }
        });
        // Observe Gboard's own setting reads. No dependency on obfuscated preference classes.
        Class<?> preferences = XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl", loader);
        if (preferences != null) hooks.hook(preferences.getDeclaredMethod("getString", String.class, String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if ("pinyin_scheme".equals(p.args[0]) && !p.hasThrowable()) controller.schemeRead((String)p.getResult());
            }
        });
        // The typed EditorInfo callback identifies the Chinese QWERTY pipeline across obfuscation.
        Class<?> processor = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.pinyin.HmmPinyinQwertyDecodeProcessor", loader);
        Class<?> hmm = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.hmm.AbstractHmmDecodeProcessor", loader);
        Class<?> chinese = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.chinese.ime.hmm.AbstractHmmChineseDecodeProcessor", loader);
        if(inlineHooks==null) inlineHooks = new InlineUppercaseHooks(loader, controller, chinese, processor);
        InlineUppercaseHooks inline = inlineHooks;
        controller.inlineSupported(inline.supported());
        EmojiSuggestions emojiSuggestions = emojiHooks;
        if (emojiSuggestions == null && chinese != null && hmm != null) try {
            Class<?> base = hmm.getSuperclass();
            while (base != null && !base.getSimpleName().equals("BaseDecodeProcessor")) base = base.getSuperclass();
            if (base == null) throw new NoSuchMethodException("Base decode processor");
            emojiSuggestions = new EmojiSuggestions(base,hmm,chinese,controller);
            emojiSuggestions.install();emojiHooks=emojiSuggestions;
        } catch (ReflectiveOperationException | RuntimeException e) { logFailure("emoji adapter",e); }
        final EmojiSuggestions emoji = emojiSuggestions;
        // LatinIme has a separate decoder hierarchy. Clear the previous mode on
        // common IME activation; an actual Pinyin decoder can then opt back in.
        Class<?> ime = XposedHelpers.findClassIfExists("com.google.android.libraries.inputmethod.ime.AbstractIme", loader);
        int imeActivations = 0;
        if (ime != null) for (Method method : ime.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || args.length < 2 || args[0] != EditorInfo.class || args[1] != boolean.class) continue;
            hooks.hook(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { controller.imeActivated(); }
            });
            imeActivations++;
        }
        if (processor != null) for (Class<?> type = processor; type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (args.length == 2 && args[0] == EditorInfo.class && args[1] == boolean.class) {
                    hooks.hook(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (processor.isInstance(p.thisObject) || hmm != null && hmm.isInstance(p.thisObject)) {
                                controller.decoderStarted(processor.isInstance(p.thisObject), chinese != null && chinese.isInstance(p.thisObject));
                                if (emoji != null && chinese != null && chinese.isInstance(p.thisObject)) emoji.watchDecoder(p.thisObject.getClass());
                            }
                        }
                    });
                }
            }
        }
        EnginePreeditAdapter preedit = EnginePreeditAdapter.discover(chinese);
        if (preedit != null && processor != null) {
            for (Method reader : preedit.readers) hooks.hook(reader, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!processor.isInstance(p.thisObject) || p.hasThrowable()) return;
                    try {
                        CharSequence raw = preedit.cached(p.thisObject);
                        CharSequence segmented = (CharSequence)p.getResult();
                        String pending = controller.engineComposition(raw, segmented);
                        reportInternalRead(segmented, preedit.cachedText != null, !pending.isEmpty());
                    } catch (ReflectiveOperationException | RuntimeException e) { logFailure("internal preedit read", e); }
                }
            });
            for (Method callback : preedit.callbacks) hooks.hook(callback, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!processor.isInstance(p.thisObject) || p.hasThrowable()) return;
                    try {
                        // Never overwrite the formatter's separated result with
                        // the unsegmented cache at the end of the same callback.
                        if (preedit.cacheWasCleared(p.thisObject)) controller.engineComposition(null);
                    } catch (ReflectiveOperationException | RuntimeException e) { logFailure("engine preedit", e); }
                }
            });
            for (Method notification : preedit.clearNotifications) hooks.hook(notification, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!processor.isInstance(p.thisObject) || p.hasThrowable()) return;
                    try {
                        if (preedit.cacheWasCleared(p.thisObject)) {
                            controller.engineComposition(null);
                            if (observedReads.add("engine cache cleared")) XposedBridge.log("GboardHints: engine cache cleared");
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) { logFailure("preedit clear", e); }
                }
            });
            if (hmm != null) for (Method method : hmm.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())
                        || args.length != 1 || args[0] != CharSequence.class) continue;
                hooks.hook(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        CharSequence text = (CharSequence)p.args[0];
                        if (!p.hasThrowable() && processor.isInstance(p.thisObject) && (text == null || text.length() == 0)) {
                            try {
                                if (preedit.cachedText == null || preedit.cacheWasCleared(p.thisObject)) controller.engineComposition(null);
                            } catch (ReflectiveOperationException | RuntimeException e) { logFailure("preedit reset read", e); }
                        }
                    }
                });
            }
        }
        controller.recoverKeys(keys,metadata);
        StartupDiagnostics.record("keyboard.hooks.installed","metadata",metadata!=null,"bindingCount",bindings,
                "inlineSupported",inline.supported(),"emojiSupported",emoji!=null,"enginePreedit",preedit!=null,
                "keyClass",keys.getName(),"serviceClass",serviceType.getName(),"loader",StartupDiagnostics.loader(loader));
        XposedBridge.log("GboardHints: installed; metadata=" + (metadata != null) + ", bindings=" + bindings
                + ", imeActivations=" + imeActivations + ", enginePreedit=" + (preedit != null)
                + ", cachedPreedit=" + (preedit != null && preedit.cachedText != null)
                + ", preeditReaders=" + (preedit == null ? 0 : preedit.readers.size())
                + ", clearNotifications=" + (preedit == null ? 0 : preedit.clearNotifications.size()));
    }
    private static final Set<String> observedReads = Collections.synchronizedSet(new HashSet<>());
    private static void reportInternalRead(CharSequence text, boolean cache, boolean pending) {
        boolean separator = false;
        if (text != null) for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) { separator = true; break; }
        }
        // Bounded, structural diagnostics only. No input characters are logged.
        String state = "internal read; cache=" + cache + ", nonempty=" + (text != null && text.length() > 0)
                + ", separated=" + separator + ", pending=" + pending;
        if (observedReads.add(state)) XposedBridge.log("GboardHints: " + state);
    }
    private static Method implementation(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private synchronized void watchConnection(Class<?> type, HintController controller) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                String name = method.getName();
                boolean composing = name.equals("setComposingText");
                boolean clearing = name.equals("commitText") || name.equals("finishComposingText")
                        || name.equals("deleteSurroundingText") || name.equals("setSelection");
                if ((!composing && !clearing) || Modifier.isAbstract(method.getModifiers())) continue;
                Class<?>[] args = method.getParameterTypes();
                if (composing && (args.length == 0 || args[0] != CharSequence.class)) continue;
                if (!connectionHooks.add(method)) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable() || Boolean.FALSE.equals(p.getResult())) return;
                            controller.composition(composing ? (CharSequence)p.args[0] : null);
                        }
                    });
                } catch (RuntimeException e) { logFailure("connection", e); }
            }
        }
    }
    static void event(String state) {
        if(reportedFailures.add("state: "+state)) {
            android.util.Log.i("GboardHints",state);
            try {XposedBridge.log("GboardHints: "+state);} catch(RuntimeException ignored) { }
        }
    }
    static void logFailure(String phase, Throwable error) {
        StartupDiagnostics.failure(phase,error);
        // Production logging omits preference values and editor/input contents.
        String key = phase + ": " + error.getClass().getSimpleName();
        if (reportedFailures.add(key)) XposedBridge.log("GboardHints: " + key);
    }
}
