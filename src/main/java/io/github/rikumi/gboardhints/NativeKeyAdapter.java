/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;

/** Copies native key definitions; Gboard keeps ownership of drawing, hit testing and decoding. */
final class NativeKeyAdapter {
    final Field metadata;
    private final ModernNativeKeyAdapter modern;
    private final Field contentHost;
    private final Constructor<?> viewConstructor;
    private final Method binder;
    private final Field labels, bindings, phase, actions, code, data, intention;
    private final Constructor<?> actionConstructor;
    private final Object decode, commit;
    NativeKeyAdapter(Class<?> view, Field metadata) throws ReflectiveOperationException {
        this.metadata = metadata;
        metadata.setAccessible(true);
        Field host=null;
        for(Field f:view.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()) && f.getType()==android.view.ViewGroup.class) {
            if(host!=null)throw new NoSuchFieldException("Ambiguous native content host");host=f;
        }
        if(host!=null)host.setAccessible(true);
        contentHost=host;

        viewConstructor = view.getConstructor(Context.class);
        Class<?> definition = metadata.getType();
        modern = definition.getName().endsWith(".metadata.SoftKeyDef") ? new ModernNativeKeyAdapter(definition) : null;
        if (modern != null) {
            labels=bindings=phase=actions=code=data=intention=null;
            actionConstructor=null;decode=commit=null;
            binder=findBinder(view,definition);binder.setAccessible(true);
            return;
        }
        labels = uniqueArray(definition, CharSequence.class);
        Field foundBindings = null, foundPhase = null, foundActions = null;
        Constructor<?> foundConstructor = null;
        Field foundCode = null, foundData = null, foundIntention = null;
        Object foundDecode = null, foundCommit = null;
        for (Field array : definition.getDeclaredFields()) {
            if (Modifier.isStatic(array.getModifiers()) || !array.getType().isArray()) continue;
            Class<?> group = array.getType().getComponentType();
            if (group.isPrimitive() || group == Object.class || group == CharSequence.class) continue;
            for (Field f : group.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !f.getType().isArray()) continue;
                Class<?> action = f.getType().getComponentType();
                for (Constructor<?> ctor : action.getDeclaredConstructors()) {
                    Class<?>[] args = ctor.getParameterTypes();
                    if (args.length != 3 || args[0] != int.class || !args[1].isEnum() || args[2] != Object.class) continue;
                    Object d = enumValue(args[1], "DECODE"), c = enumValue(args[1], "COMMIT");
                    if (d == null || c == null) continue;
                    Field p = null;
                    for (Field candidate : group.getDeclaredFields())
                        if (!Modifier.isStatic(candidate.getModifiers()) && candidate.getType().isEnum()
                                && enumValue(candidate.getType(), "PRESS") != null) p = candidate;
                    if (p == null || foundBindings != null) throw new NoSuchFieldException("Ambiguous native action binding");
                    foundBindings = array; foundPhase = p; foundActions = f; foundConstructor = ctor;
                    foundCode = uniqueField(action, int.class); foundData = uniqueField(action, Object.class);
                    foundIntention = uniqueField(action, args[1]); foundDecode = d; foundCommit = c;
                }
            }
        }
        if (foundBindings == null) throw new NoSuchFieldException("Native press binding");
        bindings = foundBindings; phase = foundPhase; actions = foundActions; actionConstructor = foundConstructor;
        code = foundCode; data = foundData; intention = foundIntention; decode = foundDecode; commit = foundCommit;
        binder = findBinder(view,definition);
        for (AccessibleObject member : new AccessibleObject[]{labels, bindings, phase, actions, code, data, intention, actionConstructor, binder}) member.setAccessible(true);
    }
    private static Method findBinder(Class<?> view,Class<?> definition) throws NoSuchMethodException {
        Method foundBinder = null;
        for (boolean withFlags : new boolean[]{true,false}) {
            for (Method method : view.getDeclaredMethods()) {
                Class<?>[] signature = withFlags ? new Class<?>[]{definition,long.class} : new Class<?>[]{definition};
                if (!Modifier.isStatic(method.getModifiers()) && Arrays.equals(method.getParameterTypes(),signature)) {
                    if (foundBinder != null) throw new NoSuchMethodException("Ambiguous native key binder");
                    foundBinder = method;
                }
            }
            if (foundBinder != null) break;
        }
        if (foundBinder == null) throw new NoSuchMethodException("Native key binder");
        return foundBinder;
    }
    View content(View view) throws IllegalAccessException {
        Object host=contentHost==null?null:contentHost.get(view);
        return host instanceof View?(View)host:view;
    }
    void content(View view,View host) throws IllegalAccessException {
        if(contentHost!=null)contentHost.set(view,host==view?null:host);
        else if(host!=view)throw new IllegalAccessException("No native content host");
    }
    Object normalFace(Object source,View model) throws ReflectiveOperationException {
        Object face=definition(model);
        if(modern!=null)return modern.normalFace(source,face);
        Object result=copy(source);
        for(Field field:source.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()) && field.getType()==int.class) {
            field.setAccessible(true);int value=field.getInt(face);
            try {if("layout".equals(model.getResources().getResourceTypeName(value)))field.setInt(result,value);}
            catch(android.content.res.Resources.NotFoundException ignored){ }
        }
        return result;
    }
    static String name(View view) {
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (android.content.res.Resources.NotFoundException e) { return ""; }
    }
    Object definition(View key) throws IllegalAccessException { return metadata.get(key); }
    View create(Context context) throws ReflectiveOperationException { return (View)viewConstructor.newInstance(context); }
    View createLike(View template) throws ReflectiveOperationException {
        if(android.os.Build.VERSION.SDK_INT>=29) {
            int layout=template.getSourceLayoutResId();
            if(layout!=0)try {
                View inflated=android.view.LayoutInflater.from(template.getContext()).inflate(layout,null,false);
                View copy=inflated.findViewById(template.getId());
                if(copy!=null && viewConstructor.getDeclaringClass().isInstance(copy)) {
                    if(copy.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)copy.getParent()).removeView(copy);
                    return copy;
                }
            } catch(android.content.res.Resources.NotFoundException | android.view.InflateException ignored) { }
        }

        View created=create(template.getContext());
        for(Field field:metadata.getDeclaringClass().getDeclaredFields())if(Modifier.isPublic(field.getModifiers())
                && Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive()) {
            field.setAccessible(true);field.set(created,field.get(template));
        }
        return created;
    }

    long flags(View view) throws IllegalAccessException {
        for (Field field : view.getClass().getDeclaredFields()) if (Modifier.isPublic(field.getModifiers())
                && !Modifier.isStatic(field.getModifiers()) && field.getType() == long.class) return field.getLong(view);
        return 0L;
    }
    void bind(View view, Object definition) throws ReflectiveOperationException { bind(view,definition,flags(view)); }
    void bind(View view, Object definition, long flags) throws ReflectiveOperationException { if (binder.getParameterTypes().length == 2) binder.invoke(view, definition, flags); else binder.invoke(view,definition); }
    Object key(View template, int id, String label, String text, boolean decodeText) throws ReflectiveOperationException {
        return key(definition(template),template,id,label,text,decodeText);
    }
    Object key(Object source, View template, int id, String label, String text, boolean decodeText) throws ReflectiveOperationException {
        if (modern != null) return modern.key(source,definition(template),template,id,label,text,decodeText);
        Object result = copy(source);
        CharSequence[] nativeLabels = (CharSequence[])labels.get(source);
        CharSequence[] replacement = new CharSequence[Math.max(1, nativeLabels == null ? 0 : nativeLabels.length)];
        if(label!=null){Arrays.fill(replacement, ""); replacement[0] = label; labels.set(result, replacement);}
        Object press = null;
        for (Object group : (Object[])bindings.get(source)) if (phase.get(group) == enumValue(phase.getType(), "PRESS")) { press = group; break; }
        if (press == null) for (Object group : (Object[])bindings.get(definition(template)))
            if (phase.get(group) == enumValue(phase.getType(), "PRESS")) {press=group;break;}
        if (press == null) throw new NoSuchFieldException("Native PRESS action");
        Object clonedPress = copy(press);
        Object action = actionConstructor.newInstance(text.equals("'") ? 75 : 74, decodeText ? decode : commit, text);
        Object array = Array.newInstance(actions.getType().getComponentType(), 1); Array.set(array, 0, action); actions.set(clonedPress, array);
        // Clear stale accessible action text inherited from the letter template.
        for (Field field : clonedPress.getClass().getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String[].class) {
            field.setAccessible(true); String[] previous = (String[])field.get(clonedPress);
            String[] strings = new String[Math.max(1, previous == null ? 0 : previous.length)]; Arrays.fill(strings, text); field.set(clonedPress, strings);
        }
        Object groups = Array.newInstance(bindings.getType().getComponentType(), 1); Array.set(groups, 0, clonedPress); bindings.set(result, groups);
        for (Field field : result.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (label != null && field.getType() == String.class && field.get(result) != null) field.set(result, label);
            if (field.getType() == int.class && Modifier.isPublic(field.getModifiers())) {
                int value = field.getInt(source);
                try { if ("id".equals(template.getResources().getResourceTypeName(value))) field.setInt(result,id); }
                catch (android.content.res.Resources.NotFoundException ignored) { }
            }
        }
        return result;
    }
    void register(View keyboard, View key) throws ReflectiveOperationException {
        for (Class<?> type = keyboard.getClass(); type != View.class; type = type.getSuperclass()) {
            Method found = null;
            for (Method method : type.getDeclaredMethods()) if (Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == void.class && Arrays.equals(method.getParameterTypes(), new Class<?>[]{View.class})) {
                if (found != null) throw new NoSuchMethodException("Ambiguous native registration"); found = method;
            }
            if (found != null) {
                found.setAccessible(true); found.invoke(keyboard,key);
                // Drop the cached proximity map so the new key participates in gestures.
                for (Field f : type.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) {
                    try { f.getType().getDeclaredConstructor(type, SparseArray.class); f.setAccessible(true); f.set(keyboard,null); }
                    catch (NoSuchMethodException ignored) { }
                }
                return;
            }
        }
        throw new NoSuchMethodException("Native keyboard registration");
    }
    String pressText(Object definition) throws ReflectiveOperationException {
        if (modern != null) return modern.pressText(definition);
        if (definition == null) return "";
        for (Object group : (Object[])bindings.get(definition)) if (phase.get(group) == enumValue(phase.getType(),"PRESS")) {
            Object[] a = (Object[])actions.get(group); return a.length == 0 ? "" : String.valueOf(data.get(a[0]));
        }
        return "";
    }
    private static Object copy(Object original) throws ReflectiveOperationException {
        Class<?> type = original.getClass(); Object result = null;
        try { Constructor<?> ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); result = ctor.newInstance(); }
        catch (NoSuchMethodException ignored) { }
        if (result == null) for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.getParameterTypes().length != 1) continue;
            try {
                Constructor<?> builder = ctor.getParameterTypes()[0].getDeclaredConstructor(); builder.setAccessible(true); ctor.setAccessible(true);
                result = ctor.newInstance(builder.newInstance()); break;
            } catch (NoSuchMethodException ignored) { }
        }
        if (result == null) throw new NoSuchMethodException("Native copy builder");
        for (Field field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())
                && !(Modifier.isPrivate(field.getModifiers()) && field.getType() == int.class)) {
            field.setAccessible(true); field.set(result,field.get(original));
        }
        return result;
    }
    private static Field uniqueArray(Class<?> type, Class<?> component) throws NoSuchFieldException { return uniqueField(type,Array.newInstance(component,0).getClass()); }
    private static Field uniqueField(Class<?> type, Class<?> kind) throws NoSuchFieldException {
        Field result = null;
        for (Field field : type.getDeclaredFields()) if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && field.getType() == kind) {
            if (result != null) throw new NoSuchFieldException("Ambiguous field"); result = field;
        }
        if (result == null) throw new NoSuchFieldException(kind.getName()); return result;
    }
    private static Object enumValue(Class<?> type, String name) {
        if (type.isEnum()) for (Object value : type.getEnumConstants()) if (((Enum<?>)value).name().equals(name)) return value;
        return null;
    }
}
