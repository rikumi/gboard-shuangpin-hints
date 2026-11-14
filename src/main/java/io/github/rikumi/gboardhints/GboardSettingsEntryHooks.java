/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import io.github.rikumi.gboardhints.bridge.*;

final class GboardSettingsEntryHooks {
    private static final java.util.Map<Class<?>,State> installed=new java.util.HashMap<>();
    private static final class State {
        final HookRegistry hooks=new HookRegistry();
        final GboardSettingsEntryAdapter adapter;
        State(Class<?> preference,Class<?> group,Class<?> list) throws ReflectiveOperationException {
            adapter=new GboardSettingsEntryAdapter(preference,group,list);
        }
    }
    static synchronized void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> preference = XposedHelpers.findClassIfExists("androidx.preference.Preference", loader);
        Class<?> group = XposedHelpers.findClassIfExists("androidx.preference.PreferenceGroup", loader);
        Class<?> list = XposedHelpers.findClassIfExists("androidx.preference.ListPreference", loader);
        if (preference == null || group == null || list == null) throw new ClassNotFoundException("Gboard preferences");
        try {
            State state=installed.get(preference);
            if(state==null){state=new State(preference,group,list);installed.put(preference,state);}
            GboardSettingsEntryAdapter adapter=state.adapter;
            HookRegistry hooks=state.hooks;
            hooks.constructors(group,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { adapter.observe(p.thisObject); }
            });
            for(Method method:preference.getDeclaredMethods()) if(Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers()) && !Modifier.isAbstract(method.getModifiers()) && method.getReturnType()==void.class && method.getParameterTypes().length<=1) {
                hooks.hook(method,new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if(p.hasThrowable())return;
                        try {adapter.observePreference(p.thisObject);adapter.reconcileAll();}
                        catch(ReflectiveOperationException | RuntimeException e){HintsModule.logFailure("settings entry reconcile",e);}
                    }
                });
            }
            Class<?> screen=XposedHelpers.findClassIfExists("androidx.preference.PreferenceScreen",loader);
            Class<?> common=XposedHelpers.findClassIfExists("com.google.android.libraries.inputmethod.preferencewidgets.CommonPreferenceFragment",loader);
            if(common!=null) {
                for(Method inflation:SettingsInflation.find(common,group)) {
                    hooks.hook(inflation,new XC_MethodHook(){
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if(p.hasThrowable() || p.getResult()==null)return;
                            try {adapter.observeTree(p.getResult());adapter.reconcileAll();adapter.diagnosticSummary("inflation:"+inflation.getDeclaringClass().getName()+"#"+inflation.getName());}
                            catch(ReflectiveOperationException | RuntimeException error){HintsModule.logFailure("native settings inflation",error);}
                        }
                    });
                    for(Method caller:inflation.getDeclaringClass().getDeclaredMethods())hooks.deoptimize(caller);
                }
                for(Class<?> type=common;type!=Object.class;type=type.getSuperclass())
                    for(Method caller:type.getDeclaredMethods())hooks.deoptimize(caller);
            }
            java.util.Set<Method> screenHooks=new java.util.HashSet<>();
            if(screen!=null && common!=null)for(Class<?> type=common;type!=Object.class;type=type.getSuperclass())
                for(Method method:type.getDeclaredMethods())if(!Modifier.isStatic(method.getModifiers())
                        && !Modifier.isAbstract(method.getModifiers()) && java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{screen})
                        && screenHooks.add(method)) {
                    hooks.hook(method,new XC_MethodHook(){
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if(p.args[0]==null)return;
                            try {adapter.observeTree(p.args[0]);adapter.reconcileAll();adapter.diagnosticSummary("screen:"+method.getDeclaringClass().getName()+"#"+method.getName());}
                            catch(ReflectiveOperationException | RuntimeException e){HintsModule.logFailure("settings screen adapter",e);}
                        }
                    });
                }
            int bindings = 0;
            for (Method method : group.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
                Class<?>[] args = method.getParameterTypes();
                if (args.length == 1 && args[0] == preference
                        && (method.getReturnType() == void.class || method.getReturnType() == boolean.class)) {
                    hooks.hook(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            try { adapter.afterOperation(p.thisObject, method, p.args[0]); }
                            catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry", e); }
                        }
                    });
                    bindings++;
                } else if (args.length == 0 && method.getReturnType() == void.class) {
                    // Native adapters sort the child list before building their rows.
                    hooks.hook(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            try { adapter.reorder(p.thisObject); }
                            catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry order", e); }
                        }
                    });
                }
            }
            StartupDiagnostics.record("settings.hooks.installed","bindings",bindings,"preferenceClass",preference.getName(),"loader",StartupDiagnostics.loader(loader));
            XposedBridge.log("GboardHints: settings entry bindings=" + bindings);
        } catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry discovery", e); throw e; }
    }
}
