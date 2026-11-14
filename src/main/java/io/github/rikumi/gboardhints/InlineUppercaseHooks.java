/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.view.inputmethod.EditorInfo;
import java.lang.reflect.*;
import java.util.*;
import io.github.rikumi.gboardhints.bridge.*;

final class InlineUppercaseHooks {
    final InlineUppercaseAdapter adapter;
    private final Set<Method> hooked = new HashSet<>();
    private final Set<Class<?>> watchedTypes = new HashSet<>();
    private final Set<String> observed = Collections.synchronizedSet(new HashSet<>());
    private final HintController controller;
    private final Class<?> chinese, pinyin;
    private volatile java.lang.ref.WeakReference<Object> currentIme=new java.lang.ref.WeakReference<>(null);
    private final InlineUppercaseDispatcher dispatcher;
    private boolean installed;
    private final EnginePreeditAdapter preedit;
    boolean supported() { return installed; }
    InlineUppercaseHooks(ClassLoader loader, HintController controller, Class<?> chinese, Class<?> pinyin) {
        this.controller = controller; this.chinese = chinese;this.pinyin=pinyin;
        preedit=EnginePreeditAdapter.discover(chinese);
        adapter = InlineUppercaseAdapter.discover(XposedHelpers.findClassIfExists(
                "com.google.android.libraries.inputmethod.ime.processor.ProcessorBasedIme", loader));
        dispatcher = adapter == null ? null : new InlineUppercaseDispatcher(adapter, controller);
        if (adapter == null || chinese == null) { status("inline uppercase dispatch unsupported"); return; }
        try {
            XposedBridge.hookMethod(adapter.dispatch, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        currentIme=new java.lang.ref.WeakReference<>(p.thisObject);
                        Object decoder = adapter.currentDecoder(p.thisObject);
                        if (decoder == null) return;
                        boolean isChinese = chinese.isInstance(decoder);
                        observePrimary(decoder,isChinese);
                        if (isChinese) { watchDecoder(decoder); syncComposition(decoder); }
                        if (dispatcher.handle(p.args[0], isChinese)) { p.setResult(true); status("inline separator consumed at IME dispatch"); }
                    } catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("inline IME event", e); }
                }
            });
            installed = true;
            // Read the primary decoder after the actual IME activation, rather
            // than trusting callbacks from all auxiliary HMM processors.
            for (Method method : adapter.dispatch.getDeclaringClass().getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (method.getReturnType() != void.class || args.length < 2
                        || args[0] != EditorInfo.class || args[1] != boolean.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) return;
                        try {
                            currentIme=new java.lang.ref.WeakReference<>(p.thisObject);
                            Object decoder = adapter.currentDecoder(p.thisObject);
                            if (decoder != null) {
                                boolean isChinese = chinese.isInstance(decoder);
                                observePrimary(decoder,isChinese);
                                if (isChinese) { watchDecoder(decoder); syncComposition(decoder); }
                                status("inline primary decoder observed; Chinese=" + isChinese);
                            }
                        } catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("inline primary decoder", e); }
                    }
                });
            }
            watchType(pinyin);
            status("inline uppercase IME and decoder hooks installed");
        } catch (RuntimeException e) { HintsModule.logFailure("inline uppercase install", e); }
    }
    synchronized void watchDecoder(Object decoder) { watchType(decoder.getClass()); }
    private synchronized void watchType(Class<?> decoderType) {
        if (!installed || decoderType == null || !watchedTypes.add(decoderType)) return;
        for (Class<?> type = decoderType; type != null; type = type.getSuperclass())
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isAbstract(modifiers) || Modifier.isStatic(modifiers) || method.getReturnType() != boolean.class) continue;
                boolean admission = Modifier.isPublic(modifiers) && Arrays.equals(method.getParameterTypes(), new Class<?>[]{adapter.actionType});
                boolean decoderEvent = adapter.decoderEventMethod(method);
                if ((!admission && !decoderEvent) || !hooked.add(method)) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (!chinese.isInstance(p.thisObject)) return;
                            try {
                                syncComposition(p.thisObject);
                                if (admission) {
                                    if (dispatcher.acceptsUppercase(p.args[0], true)) { p.setResult(true); status("inline uppercase admitted to Chinese decoder"); }
                                } else if (dispatcher.handle(p.args[0], true)) {
                                    p.setResult(true); status("inline separator consumed at Chinese decoder");
                                }
                            } catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("inline Chinese event", e); }
                        }
                    });
                } catch (RuntimeException e) { HintsModule.logFailure("inline decoder hook", e); }
            }
    }
    private void observePrimary(Object decoder,boolean isChinese) {
        StartupDiagnostics.changed("decoder.primary.class",decoder.getClass().getName(),"class",decoder.getClass().getName());
        boolean isPinyin=pinyin!=null && pinyin.isInstance(decoder);
        String scheme=null;
        if(isPinyin)try{scheme=NativeScheme.read(decoder);}
        catch(ReflectiveOperationException | RuntimeException | LinkageError error){HintsModule.logFailure("native scheme backend",error);}
        controller.primaryDecoderObserved(isPinyin,isChinese,scheme);
    }
    void refreshCurrent() {
        Object ime=currentIme.get();if(ime==null)return;
        try {
            Object decoder=adapter.currentDecoder(ime);if(decoder==null)return;
            boolean isChinese=chinese.isInstance(decoder);
            observePrimary(decoder,isChinese);
            if(isChinese){watchDecoder(decoder);syncComposition(decoder);}
        }catch(ReflectiveOperationException | RuntimeException error){HintsModule.logFailure("primary mode refresh",error);}
    }
    private void syncComposition(Object decoder) throws ReflectiveOperationException {
        if(preedit!=null && controller.inlineUppercaseConfigured())controller.engineComposition(preedit.read(decoder));
    }
    private void status(String message) { if (observed.add(message)) XposedBridge.log("GboardHints: " + message); }
}
