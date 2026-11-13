/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.*;
import java.util.*;

/** Uses Gboard's own Preference classes, theme, group lifecycle and click handling. */
final class GboardSettingsEntryAdapter {
    static final String KEY = "io.github.rikumi.gboardhints.settings";
    static final String TITLE = "双拼提示";
    final Class<?> preference, group, listPreference;
    private final Constructor<?> constructor;
    private final Field children, context, intent, title;
    private final List<Field> strings = new ArrayList<>();

    GboardSettingsEntryAdapter(Class<?> preference, Class<?> group, Class<?> listPreference) throws ReflectiveOperationException {
        this.preference = preference; this.group = group; this.listPreference = listPreference;
        constructor = preference.getConstructor(Context.class);
        children = uniqueField(group, List.class, false);
        context = uniqueField(preference, Context.class, false);
        intent = uniqueField(preference, Intent.class, false);
        title = uniqueField(preference, CharSequence.class, true);
        for (Field field : preference.getDeclaredFields())
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true); strings.add(field);
            }
    }
    private static Field uniqueField(Class<?> owner, Class<?> type, boolean publicOnly) throws NoSuchFieldException {
        Field result = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != type
                    || publicOnly && !Modifier.isPublic(field.getModifiers())) continue;
            if (result != null) throw new NoSuchFieldException("Ambiguous " + type.getSimpleName());
            result = field;
        }
        if (result == null) throw new NoSuchFieldException(type.getSimpleName());
        result.setAccessible(true); return result;
    }
    private Field keyField(Object value, String expected) throws IllegalAccessException {
        Field result = null;
        for (Field field : strings) if (expected.equals(field.get(value))) {
            if (result != null) return null;
            result = field;
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private List<Object> children(Object owner) throws IllegalAccessException { return (List<Object>)children.get(owner); }

    void afterOperation(Object owner, Method operation, Object item) throws ReflectiveOperationException {
        List<Object> items = children(owner);
        if (!listPreference.isInstance(item) || keyField(item, "pinyin_scheme") == null) {
            reorder(owner); return;
        }
        Field key = keyField(item, "pinyin_scheme");
        Object existing = null;
        for (Object value : new ArrayList<>(items)) if (KEY.equals(key.get(value))) { existing = value; break; }
        if (!items.contains(item)) {
            // The same structural callback can also be a native remove operation.
            if (existing != null) operation.invoke(owner, existing);
            return;
        }
        if (existing == null) {
            Context ctx = (Context)context.get(item);
            Object entry = constructor.newInstance(ctx);
            key.set(entry, KEY); title.set(entry, TITLE);
            intent.set(entry, new Intent().setComponent(new ComponentName("io.github.rikumi.gboardhints",
                    "io.github.rikumi.gboardhints.SettingsActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // A fresh Preference's order is the unique integer defaulted to MAX_VALUE.
            Field order = null;
            for (Field field : preference.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
                field.setAccessible(true);
                if (field.getInt(entry) == Integer.MAX_VALUE) {
                    if (order != null) throw new NoSuchFieldException("Ambiguous preference order");
                    order = field;
                }
            }
            if (order == null) throw new NoSuchFieldException("Preference order");
            order.setInt(entry, order.getInt(item));
            operation.invoke(owner, entry);
        }
        reorder(owner);
    }

    void reorder(Object owner) throws IllegalAccessException {
        List<Object> items = children(owner);
        Object anchor = null, entry = null;
        for (Object item : new ArrayList<>(items)) {
            if (listPreference.isInstance(item) && keyField(item, "pinyin_scheme") != null) anchor = item;
            if (keyField(item, KEY) != null) entry = item;
        }
        if (anchor == null || entry == null || items.indexOf(entry) == items.indexOf(anchor) + 1) return;
        synchronized (owner) {
            items.remove(entry);
            items.add(items.indexOf(anchor) + 1, entry);
        }
    }
}
