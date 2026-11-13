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
    private final Field parent;
    private final List<Field> strings = new ArrayList<>();
    private final Set<Object> observed = Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    private final List<Method> mutations = new ArrayList<>();
    private Method confirmedAdd;
    private Field orderField;
    private boolean orderChecked;
    private boolean reconciling;

    GboardSettingsEntryAdapter(Class<?> preference, Class<?> group, Class<?> listPreference) throws ReflectiveOperationException {
        this.preference = preference; this.group = group; this.listPreference = listPreference;
        constructor = preference.getConstructor(Context.class);
        for(Method m:group.getDeclaredMethods()) if(Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
                && Arrays.equals(m.getParameterTypes(),new Class<?>[]{preference})
                && (m.getReturnType()==void.class || m.getReturnType()==boolean.class)) {m.setAccessible(true);mutations.add(m);}

        children = uniqueField(group, List.class, false);
        Field owner=null;
        for(Field field:preference.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()) && field.getType()==group){field.setAccessible(true);owner=field;break;}
        parent=owner;
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
        observed.add(owner);
        List<Object> items = children(owner);
        if (items.contains(item)) confirmedAdd = operation;
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
            // Let the group allocate its next order slot and advance its counter.
            // Reusing the anchor's order lets later adapter sorting move us above it.
            operation.invoke(owner, entry);
        }
        reorder(owner);
    }

    void observePreference(Object item) throws IllegalAccessException {
        if(group.isInstance(item))observe(item);
        if(parent!=null && preference.isInstance(item)) {
            Object owner=parent.get(item);
            if(owner!=null)observe(owner);
        }
    }
    void observeTree(Object owner) throws IllegalAccessException {
        if(!group.isInstance(owner))return;
        observe(owner);
        List<Object> list=children(owner);
        if(list!=null)for(Object child:new ArrayList<>(list))if(group.isInstance(child))observeTree(child);
    }
    void observe(Object owner) { observed.add(owner); }

    // R8 can inline inflation/addition, so operation hooks alone do not cover all settings builds.
    void reconcileAll() throws ReflectiveOperationException {
        if(reconciling)return;
        reconciling=true;
        try {
            ReflectiveOperationException failure=null;
            RuntimeException runtime=null;
            for(Object owner:new ArrayList<>(observed)) try {
                List<Object> items=children(owner);if(items==null)continue;
                Object anchor=null;boolean present=false;
                for(Object child:new ArrayList<>(items)) {
                    if(listPreference.isInstance(child) && keyField(child,"pinyin_scheme")!=null)anchor=child;
                    if(keyField(child,KEY)!=null)present=true;
                }
                if(anchor==null)continue;
                if(!present) {
                    Field key=keyField(anchor,"pinyin_scheme");Object entry=constructor.newInstance(context.get(anchor));
                    key.set(entry,KEY);title.set(entry,TITLE);
                    intent.set(entry,new Intent().setComponent(new ComponentName("io.github.rikumi.gboardhints",
                            "io.github.rikumi.gboardhints.SettingsActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    // A removal operation on this new, unattached row cannot remove any existing setting.
                    // Confirm actual insertion before retaining the operation; never probe an existing row.
                    List<Method> candidates=confirmedAdd==null?mutations:Collections.singletonList(confirmedAdd);
                    ReflectiveOperationException insertionFailure=null;
                    for(Method operation:candidates) {
                        try {operation.invoke(owner,entry);}
                        catch(ReflectiveOperationException error){items.remove(entry);insertionFailure=error;continue;}
                        if(items.contains(entry)){confirmedAdd=operation;break;}
                    }
                    if(!items.contains(entry) && insertionFailure!=null)throw insertionFailure;
                }
                reorder(owner);
            } catch(ReflectiveOperationException error){failure=error;}
              catch(RuntimeException error){runtime=error;}
            if(failure!=null)throw failure;
            if(runtime!=null)throw runtime;
        } finally { reconciling=false; }
    }

    @SuppressWarnings("unchecked")
    private void alignOrder(Object anchor,Object entry,List<Object> items) throws ReflectiveOperationException {
        if(!orderChecked) {
            orderChecked=true;
            Object a=constructor.newInstance(context.get(anchor)),b=constructor.newInstance(context.get(anchor));
            title.set(a,"order");title.set(b,"order");
            if(a instanceof Comparable) for(Field f:preference.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || f.getType()!=int.class)continue;
                f.setAccessible(true);int original=f.getInt(a),other=f.getInt(b);
                f.setInt(a,-1000);f.setInt(b,1000);int before=((Comparable<Object>)a).compareTo(b);
                f.setInt(a,1000);f.setInt(b,-1000);int after=((Comparable<Object>)a).compareTo(b);
                f.setInt(a,original);f.setInt(b,other);
                if(before<0 && after>0){orderField=f;break;}
            }
        }
        if(orderField==null)return;
        int start=orderField.getInt(anchor),end=orderField.getInt(entry);
        if(start>=Integer.MAX_VALUE-1 || end==start+1)return;
        for(Object child:items) if(child!=entry) {
            int current=orderField.getInt(child);
            if(current>start && current<Integer.MAX_VALUE && (end<=start || current<end))orderField.setInt(child,current+1);
        }
        orderField.setInt(entry,start+1);
    }

    void reorder(Object owner) throws ReflectiveOperationException {
        List<Object> items = children(owner);
        Object anchor = null, entry = null;
        for (Object item : new ArrayList<>(items)) {
            if (listPreference.isInstance(item) && keyField(item, "pinyin_scheme") != null) anchor = item;
            if (keyField(item, KEY) != null) entry = item;
        }
        if (anchor == null || entry == null) return;
        if (items.indexOf(entry) == items.indexOf(anchor) + 1) { alignOrder(anchor,entry,items); return; }
        alignOrder(anchor,entry,items);
        synchronized (owner) {
            items.remove(entry);
            items.add(items.indexOf(anchor) + 1, entry);
        }
    }
}
