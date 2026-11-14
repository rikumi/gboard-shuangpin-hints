/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import java.lang.reflect.*;
import java.util.*;

/** Discover the native preference manager's XML-to-screen boundary before adapters take a snapshot. */
final class SettingsInflation {
    static Set<Method> find(Class<?> fragment, Class<?> group) {
        Set<Method> result=new HashSet<>();
        for(Class<?> type=fragment;type!=null && type!=Object.class;type=type.getSuperclass())
            for(Field field:type.getDeclaredFields()) {
                Class<?> manager=field.getType();String name=manager.getName();
                if(Modifier.isStatic(field.getModifiers()) || manager.isPrimitive() || manager.isArray()
                        || name.startsWith("java.") || name.startsWith("android."))continue;
                for(Method method:manager.getDeclaredMethods()) {
                    if(Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                            || !group.isAssignableFrom(method.getReturnType()))continue;
                    List<Class<?>> args=Arrays.asList(method.getParameterTypes());
                    if(args.contains(Context.class) && args.contains(int.class))result.add(method);
                }
            }
        return result;
    }
}
