/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints.bridge;
import android.util.Log;
import java.lang.reflect.*;

public final class XposedBridge {
    public interface Backend {
        int getApiVersion();
        default boolean supportsRemotePreferences(){return true;}
        void log(int level,String tag,String text);
        void hook(Member member,XC_MethodHook callback);
        boolean deoptimize(Member member);
        android.content.SharedPreferences getRemotePreferences(String group);
    }
    public static Backend modern;
    public static String moduleApk;
    public static int getXposedVersion() { return modern != null ? modern.getApiVersion() : LegacyHooks.version(); }
    public static void log(String text) {
        if(modern!=null) modern.log(Log.INFO,"GboardHints",text);
        else { try { LegacyHooks.log(text); } catch(RuntimeException | LinkageError ignored) { Log.i("GboardHints",text); } }
    }
    public static void hookMethod(Member member,XC_MethodHook callback) {
        if(modern==null) { LegacyHooks.hook(member,callback);return; }
        modern.hook(member,callback);
    }
    public static void hookAllMethods(Class<?> owner,String name,XC_MethodHook callback) {
        for(Method method:owner.getDeclaredMethods())if(method.getName().equals(name))hookMethod(method,callback);
    }
    public static void deoptimizeMethod(Member method) throws ReflectiveOperationException {
        if(modern!=null)modern.deoptimize(method);else LegacyHooks.deoptimize(method);
    }
    public static android.app.Application currentApplication() {
        try {
            Class<?> thread=Class.forName("android.app.ActivityThread");
            return (android.app.Application)thread.getDeclaredMethod("currentApplication").invoke(null);
        } catch(ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
}
