/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import de.robv.android.xposed.*;

final class GboardSettingsEntryHooks {
    static void install(ClassLoader loader) {
        Class<?> preference = XposedHelpers.findClassIfExists("androidx.preference.Preference", loader);
        Class<?> group = XposedHelpers.findClassIfExists("androidx.preference.PreferenceGroup", loader);
        Class<?> list = XposedHelpers.findClassIfExists("androidx.preference.ListPreference", loader);
        if (preference == null || group == null || list == null) return;
        try {
            GboardSettingsEntryAdapter adapter = new GboardSettingsEntryAdapter(preference, group, list);
            int bindings = 0;
            for (Method method : group.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] args = method.getParameterTypes();
                if (args.length == 1 && args[0] == preference
                        && (method.getReturnType() == void.class || method.getReturnType() == boolean.class)) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            try { adapter.afterOperation(p.thisObject, method, p.args[0]); }
                            catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry", e); }
                        }
                    });
                    bindings++;
                } else if (args.length == 0 && method.getReturnType() == void.class) {
                    // Native adapters sort the child list before building their rows.
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            try { adapter.reorder(p.thisObject); }
                            catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry order", e); }
                        }
                    });
                }
            }
            XposedBridge.log("GboardHints: settings entry bindings=" + bindings);
        } catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("settings entry discovery", e); }
    }
}
