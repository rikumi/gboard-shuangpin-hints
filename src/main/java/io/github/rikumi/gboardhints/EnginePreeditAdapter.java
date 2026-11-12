/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import java.util.*;

/** Locate the Chinese decoder's update callback and unconverted preedit by shape, not obfuscated names. */
final class EnginePreeditAdapter {
    final List<Method> callbacks, readers, clearNotifications;
    final Field cachedText;
    final Method textGetter;
    private EnginePreeditAdapter(List<Method> callbacks, List<Method> readers, List<Method> clearNotifications, Field cachedText, Method textGetter) {
        this.callbacks = callbacks; this.readers = readers; this.clearNotifications = clearNotifications; this.cachedText = cachedText; this.textGetter = textGetter;
    }
    static EnginePreeditAdapter discover(Class<?> chinese) {
        if (chinese == null) return null;
        List<Field> caches = new ArrayList<>();
        List<Method> getters = new ArrayList<>(), readers = new ArrayList<>(), clears = new ArrayList<>(), updates = new ArrayList<>();
        for (Field field : chinese.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && Modifier.isVolatile(field.getModifiers())
                    && CharSequence.class.isAssignableFrom(field.getType())) caches.add(field);
        }
        for (Method method : chinese.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
            Class<?>[] args = method.getParameterTypes();
            if (Modifier.isPublic(method.getModifiers()) && method.getReturnType() == void.class
                    && args.length == 1 && args[0] == long.class) updates.add(method);
            if (args.length == 0 && method.getReturnType() == void.class) clears.add(method);
            if (args.length == 0 && method.getReturnType() == CharSequence.class) {
                if (Modifier.isProtected(method.getModifiers())) getters.add(method);
                if (Modifier.isProtected(method.getModifiers())) readers.add(method);
            }
        }
        if (updates.size() != 1 || caches.size() > 1 || caches.isEmpty() && getters.size() != 1) return null;
        Field cache = caches.isEmpty() ? null : caches.get(0);
        Method getter = cache == null ? getters.get(0) : null;
        if (cache != null) cache.setAccessible(true);
        if (getter != null) getter.setAccessible(true);
        return new EnginePreeditAdapter(updates, readers, cache == null ? Collections.emptyList() : clears, cache, getter);
    }
    CharSequence read(Object decoder) throws ReflectiveOperationException {
        return (CharSequence)(cachedText == null ? textGetter.invoke(decoder) : cachedText.get(decoder));
    }
    CharSequence cached(Object decoder) throws IllegalAccessException {
        return cachedText == null ? null : (CharSequence)cachedText.get(decoder);
    }
    boolean cacheWasCleared(Object decoder) throws IllegalAccessException {
        if (cachedText == null) return false;
        CharSequence text = cached(decoder);
        return text == null || text.length() == 0;
    }
}
