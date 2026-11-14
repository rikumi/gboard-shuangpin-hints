/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import java.util.*;
import io.github.rikumi.gboardhints.bridge.*;

/** A failed late installation can retry without duplicating earlier callbacks. */
final class HookRegistry {
    private final Set<Member> installed=new HashSet<>();
    synchronized void hook(Member member, XC_MethodHook callback) {
        if(installed.contains(member))return;
        try {
            XposedBridge.hookMethod(member,callback);installed.add(member);
            StartupDiagnostics.record("hook.installed","owner",member.getDeclaringClass().getName(),"method",member.getName(),"registry",System.identityHashCode(this));
        } catch(RuntimeException | LinkageError error) {
            StartupDiagnostics.failure("hook.install:"+member.getDeclaringClass().getName()+"#"+member.getName(),error);throw error;
        }
    }
    void constructors(Class<?> type, XC_MethodHook callback) {
        for(Constructor<?> constructor:type.getDeclaredConstructors())hook(constructor,callback);
    }
    private final Set<Member> deoptimized=new HashSet<>();
    synchronized void deoptimize(Method caller) {
        if(Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers()) || deoptimized.contains(caller))return;
        try {
            // LSPosed/LSPatch extension; keep the compile-time legacy Xposed API compatible.
            Method deopt=XposedBridge.class.getDeclaredMethod("deoptimizeMethod",Member.class);
            deopt.invoke(null,caller);deoptimized.add(caller);
        } catch(ReflectiveOperationException | RuntimeException error) {
            HintsModule.event("settings caller deoptimization unavailable");
        }
    }

}
