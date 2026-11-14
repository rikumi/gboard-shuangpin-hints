/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints.bridge;
public final class XposedHelpers {
    public static Class<?> findClassIfExists(String name,ClassLoader loader) {
        try {return Class.forName(name,false,loader);}catch(ClassNotFoundException | LinkageError ignored){return null;}
    }
    public static void findAndHookMethod(Class<?> owner,String name,Object... arguments) throws NoSuchMethodException {
        Class<?>[] types=new Class<?>[arguments.length-1];
        for(int i=0;i<types.length;i++)types[i]=(Class<?>)arguments[i];
        XposedBridge.hookMethod(owner.getDeclaredMethod(name,types),(XC_MethodHook)arguments[types.length]);
    }
}
