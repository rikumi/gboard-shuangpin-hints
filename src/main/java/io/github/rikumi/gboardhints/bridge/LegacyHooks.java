/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints.bridge;
import java.lang.reflect.*;
/** Loaded only by the legacy/LSPatch entry point; never linked by the modern entry point. */
final class LegacyHooks {
    static int version() { return de.robv.android.xposed.XposedBridge.getXposedVersion(); }
    static void log(String text) { de.robv.android.xposed.XposedBridge.log(text); }
    static void deoptimize(Member method) throws ReflectiveOperationException {
        de.robv.android.xposed.XposedBridge.class.getDeclaredMethod("deoptimizeMethod",Member.class).invoke(null,method);
    }
    static void hook(Member member,XC_MethodHook callback) {
        de.robv.android.xposed.XposedBridge.hookMethod(member,new de.robv.android.xposed.XC_MethodHook() {
            private final ThreadLocal<java.util.ArrayDeque<XC_MethodHook.MethodHookParam>> stack=ThreadLocal.withInitial(java.util.ArrayDeque::new);
            @Override protected void beforeHookedMethod(MethodHookParam nativeParam) throws Throwable {
                XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();
                p.method=member;p.thisObject=nativeParam.thisObject;p.args=nativeParam.args;
                stack.get().push(p);callback.before(p);
                nativeParam.args=p.args;
                if(p.early) { if(p.hasThrowable())nativeParam.setThrowable(p.getThrowable());else nativeParam.setResult(p.getResult()); }
            }
            @Override protected void afterHookedMethod(MethodHookParam nativeParam) throws Throwable {
                java.util.ArrayDeque<XC_MethodHook.MethodHookParam> calls=stack.get();
                if(calls.isEmpty())return;
                XC_MethodHook.MethodHookParam p=calls.pop();
                if(calls.isEmpty())stack.remove();
                p.original(nativeParam.getResult(),nativeParam.getThrowable());callback.after(p);
                if(p.hasThrowable())nativeParam.setThrowable(p.getThrowable());else nativeParam.setResult(p.getResult());
            }
        });
    }
}
