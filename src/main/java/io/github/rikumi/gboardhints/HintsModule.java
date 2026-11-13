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
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HintsModule implements IXposedHookLoadPackage {
    static final String TARGET = "com.google.android.inputmethod.latin";
    private final Set<Method> connectionHooks = new HashSet<>();
    private static final Set<String> reportedFailures = Collections.synchronizedSet(new HashSet<>());
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (!TARGET.equals(param.packageName)) return;
        GboardSettingsEntryHooks.install(param.classLoader);
        HintController controller = new HintController();
        Class<?> serviceType = XposedHelpers.findClassIfExists("com.android.inputmethod.latin.LatinIME", param.classLoader);
        if (serviceType == null) serviceType = InputMethodService.class;
        Class<?> keys = XposedHelpers.findClassIfExists(AdaptiveKeyAdapter.KEY_CLASS, param.classLoader);
        if (keys == null) { XposedBridge.log("GboardHints: unsupported key view; no hooks installed"); return; }
        XposedHelpers.findAndHookMethod(android.widget.TextView.class, "setTextSize", int.class, float.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Float size = PrimaryLetterSize.nativeRequest((android.widget.TextView)p.thisObject, (Integer)p.args[0], (Float)p.args[1]);
                if (size != null) { p.args[0] = android.util.TypedValue.COMPLEX_UNIT_SP; p.args[1] = size; }
            }
        });
        Field metadata = AdaptiveKeyAdapter.metadataField(keys);
        if (metadata != null) try { controller.nativeKeys(new NativeKeyAdapter(keys,metadata)); }
        catch (ReflectiveOperationException | RuntimeException e) { logFailure("native semicolon adapter",e); }
        XposedBridge.hookAllConstructors(keys, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { controller.observe((View)p.thisObject, metadata); }
                catch (RuntimeException e) { logFailure("observe", e); }
            }
        });
        Class<?> keyboard=XposedHelpers.findClassIfExists("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView",param.classLoader);
        if(keyboard!=null)try {
            XposedBridge.hookMethod(implementation(keyboard,"onLayout",boolean.class,int.class,int.class,int.class,int.class),new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){controller.refreshLater();}
            });
        } catch(NoSuchMethodException | RuntimeException error){logFailure("native keyboard layout",error);}
        int bindings = 0;
        if (metadata != null) for (Method method : keys.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] args = method.getParameterTypes();
            if (args.length > 0 && args[0] == metadata.getType()) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) { controller.refreshLater(); }
                });
                bindings++;
            }
        }
        try {
            XposedBridge.hookMethod(implementation(keys, "onDraw", android.graphics.Canvas.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { controller.enforceKeyState((View)p.thisObject); }
            });
        } catch (NoSuchMethodException | RuntimeException e) { logFailure("key draw enforcement", e); }
        XposedBridge.hookMethod(implementation(serviceType, "onCreate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.attach((InputMethodService)p.thisObject); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onStartInputView", EditorInfo.class, boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.start((EditorInfo)p.args[0]); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onFinishInputView", boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.finish(); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onFinishInput"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.finish(); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onUpdateSelection", int.class, int.class, int.class,
                int.class, int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int start = (Integer)p.args[4], end = (Integer)p.args[5];
                if (start < 0 || end < 0 || (Integer)p.args[2] != end || (Integer)p.args[3] != end)
                    controller.composition(null);
            }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onCurrentInputMethodSubtypeChanged", InputMethodSubtype.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { controller.subtypeChanged((InputMethodSubtype)p.args[0]); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "onDestroy"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { controller.detach(); }
        });
        XposedBridge.hookMethod(implementation(serviceType, "getCurrentInputConnection"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object result = p.getResult();
                if (result instanceof InputConnection) watchConnection(result.getClass(), controller);
            }
        });
        // Observe Gboard's own setting reads. No dependency on obfuscated preference classes.
        Class<?> preferences = XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl", param.classLoader);
        if (preferences != null) XposedHelpers.findAndHookMethod(preferences, "getString", String.class, String.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if ("pinyin_scheme".equals(p.args[0]) && !p.hasThrowable()) controller.schemeRead((String)p.getResult());
            }
        });
        // The typed EditorInfo callback identifies the Chinese QWERTY pipeline across obfuscation.
        Class<?> processor = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.pinyin.HmmPinyinQwertyDecodeProcessor", param.classLoader);
        Class<?> hmm = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.hmm.AbstractHmmDecodeProcessor", param.classLoader);
        Class<?> chinese = XposedHelpers.findClassIfExists(
                "com.google.android.apps.inputmethod.libs.chinese.ime.hmm.AbstractHmmChineseDecodeProcessor", param.classLoader);
        InlineUppercaseHooks inline = new InlineUppercaseHooks(param.classLoader, controller, chinese, processor);
        controller.inlineSupported(inline.supported());
        EmojiSuggestions emojiSuggestions = null;
        if (chinese != null && hmm != null) try {
            Class<?> base = hmm.getSuperclass();
            while (base != null && !base.getSimpleName().equals("BaseDecodeProcessor")) base = base.getSuperclass();
            if (base == null) throw new NoSuchMethodException("Base decode processor");
            emojiSuggestions = new EmojiSuggestions(base,hmm,chinese,controller);
            emojiSuggestions.install();
        } catch (ReflectiveOperationException | RuntimeException e) { logFailure("emoji adapter",e); }
        final EmojiSuggestions emoji = emojiSuggestions;
        // LatinIme has a separate decoder hierarchy. Clear the previous mode on
        // common IME activation; an actual Pinyin decoder can then opt back in.
        Class<?> ime = XposedHelpers.findClassIfExists("com.google.android.libraries.inputmethod.ime.AbstractIme", param.classLoader);
        int imeActivations = 0;
        if (ime != null) for (Method method : ime.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || args.length < 2 || args[0] != EditorInfo.class || args[1] != boolean.class) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { controller.imeActivated(); }
            });
            imeActivations++;
        }
        if (processor != null) for (Class<?> type = processor; type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (args.length == 2 && args[0] == EditorInfo.class && args[1] == boolean.class) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
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
            for (Method reader : preedit.readers) XposedBridge.hookMethod(reader, new XC_MethodHook() {
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
            for (Method callback : preedit.callbacks) XposedBridge.hookMethod(callback, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!processor.isInstance(p.thisObject) || p.hasThrowable()) return;
                    try {
                        // Never overwrite the formatter's separated result with
                        // the unsegmented cache at the end of the same callback.
                        if (preedit.cacheWasCleared(p.thisObject)) controller.engineComposition(null);
                    } catch (ReflectiveOperationException | RuntimeException e) { logFailure("engine preedit", e); }
                }
            });
            for (Method notification : preedit.clearNotifications) XposedBridge.hookMethod(notification, new XC_MethodHook() {
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
                XposedBridge.hookMethod(method, new XC_MethodHook() {
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
    static void logFailure(String phase, Throwable error) {
        // Do not log input text, preference values, or editor contents.
        String key = phase + ": " + error.getClass().getSimpleName();
        if (reportedFailures.add(key)) XposedBridge.log("GboardHints: " + key);
    }
}
