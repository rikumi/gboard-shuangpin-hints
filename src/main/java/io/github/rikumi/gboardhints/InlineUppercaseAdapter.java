/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import java.util.*;

/** Structural adapter for Gboard's immutable decode actions and IME dispatch. */
final class InlineUppercaseAdapter {
    final Method dispatch;
    final Class<?> actionType, eventType;
    private final Field actions, phase, code, data, intention;
    private final Constructor<?> constructor;
    private final Object decode;
    private InlineUppercaseAdapter(Method dispatch, Field actions, Field phase,
            Field code, Field data, Field intention, Constructor<?> constructor, Object decode) {
        this.dispatch = dispatch; this.actions = actions; this.phase = phase;
        this.code = code; this.data = data; this.intention = intention; this.constructor = constructor;
        this.decode = decode; this.actionType = actions.getType().getComponentType(); this.eventType = dispatch.getParameterTypes()[0];
    }
    static InlineUppercaseAdapter discover(Class<?> ime) {
        if (ime == null) return null;
        List<InlineUppercaseAdapter> found = new ArrayList<>();
        for (Method method : ime.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != boolean.class
                    || method.getParameterTypes().length != 1 || !inheritedInterface(ime.getSuperclass(), method)) continue;
            Class<?> event = method.getParameterTypes()[0];
            for (Field array : event.getDeclaredFields()) {
                if (Modifier.isStatic(array.getModifiers()) || !array.getType().isArray()) continue;
                Class<?> action = array.getType().getComponentType();
                Field code = null, data = null, intention = null;
                for (Field f : action.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
                    if (f.getType() == int.class) { if (code != null) { code = null; break; } code = f; }
                    if (f.getType() == Object.class) data = f;
                    if (f.getType().isEnum()) intention = f;
                }
                if (code == null || data == null || intention == null) continue;
                Object decode = null;
                for (Object e : intention.getType().getEnumConstants()) if (((Enum<?>)e).name().equals("DECODE")) decode = e;
                if (decode == null) continue;
                try {
                    Constructor<?> ctor;
                    try { ctor = action.getDeclaredConstructor(int.class, intention.getType(), Object.class); }
                    catch (NoSuchMethodException e) { ctor = action.getDeclaredConstructor(int.class, intention.getType(), Object.class,int.class); }
                    Field phase = null;
                    for (Field f : event.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isEnum()) {
                            for (Object value : f.getType().getEnumConstants())
                                if (((Enum<?>)value).name().equals("PRESS")) phase = f;
                        }
                    }
                    if (phase == null) continue;
                    for (AccessibleObject f : new AccessibleObject[]{array, phase, code, data, intention, ctor}) f.setAccessible(true);
                    found.add(new InlineUppercaseAdapter(method, array, phase, code, data, intention, ctor, decode));
                } catch (NoSuchMethodException ignored) { }
            }
        }
        return found.size() == 1 ? found.get(0) : null;
    }
    private static boolean inheritedInterface(Class<?> type, Method method) {
        if (type == null) return false;
        for (Class<?> face : type.getInterfaces()) {
            try { if (face.getMethod(method.getName(), method.getParameterTypes()).getReturnType() == boolean.class) return true; }
            catch (NoSuchMethodException ignored) { }
        }
        return inheritedInterface(type.getSuperclass(), method);
    }
    boolean decoderEventMethod(Method method) {
        int flags = method.getModifiers();
        return Modifier.isProtected(flags) && !Modifier.isAbstract(flags) && !Modifier.isStatic(flags)
                && method.getReturnType() == boolean.class && Arrays.equals(method.getParameterTypes(), new Class<?>[]{eventType});
    }
    Object currentDecoder(Object ime) throws IllegalAccessException {
        for (Class<?> host = ime.getClass(); host != null; host = host.getSuperclass()) {
            for (Field managerField : host.getDeclaredFields()) {
                if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()
                        || managerField.getType().isArray()) continue;
                String name = managerField.getType().getName();
                if (name.startsWith("java.") || name.startsWith("android.")) continue;
                managerField.setAccessible(true); Object manager = managerField.get(ime);
                if (manager == null) continue;
                Object arrayFallback = null;
                for (Field primary : manager.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(primary.getModifiers()) || primary.getType().isPrimitive()) continue;
                    primary.setAccessible(true); Object value = primary.get(manager);
                    // R8 can erase the primary decoder field to Object or an
                    // interface. Match the runtime instance, not its field type.
                    if (isDecoder(value)) return value;
                    if (value instanceof Object[]) for (Object element : (Object[])value)
                        if (isDecoder(element)) arrayFallback = element;
                }
                if (arrayFallback != null) return arrayFallback;
            }
        }
        return null;
    }
    private static boolean isDecoder(Object value) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            if (type.getSimpleName().equals("BaseDecodeProcessor")) return true;
        return false;
    }
    boolean press(Object event) throws IllegalAccessException {
        Object value = phase.get(event); return value instanceof Enum && ((Enum<?>)value).name().equals("PRESS");
    }
    int keyCode(Object event) throws IllegalAccessException {
        Object[] values = (Object[])actions.get(event); return values.length == 0 ? 0 : code.getInt(values[0]);
    }
    boolean separator(Object event) throws IllegalAccessException {
        Object[] values = (Object[])actions.get(event);
        return values.length > 0 && separatorAction(values[0]);
    }
    private boolean separatorAction(Object action) throws IllegalAccessException {
        return intention.get(action) == decode && "'".equals(data.get(action));
    }
    boolean replaceableDefinition(Object definition) {
        if (definition == null) return false;
        try {
            for (Field bindings : definition.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(bindings.getModifiers()) || !bindings.getType().isArray()
                        || bindings.getType().getComponentType().isPrimitive()) continue;
                bindings.setAccessible(true); Object[] groups = (Object[])bindings.get(definition);
                if (groups == null) continue;
                for (Object group : groups) {
                    if (group == null) continue;
                    boolean press = false;
                    for (Field f : group.getClass().getDeclaredFields()) if (f.getType().isEnum()) {
                        f.setAccessible(true); Object kind = f.get(group);
                        if (kind instanceof Enum && ((Enum<?>)kind).name().equals("PRESS")) press = true;
                    }
                    if (!press) continue;
                    for (Field f : group.getClass().getDeclaredFields()) {
                        if (f.getType().isArray() && f.getType().getComponentType() == actionType) {
                            f.setAccessible(true); Object[] values = (Object[])f.get(group);
                            if (values != null && values.length > 0 && separatorAction(values[0])) return true;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }
    boolean uppercase(Object event) throws ReflectiveOperationException {
        Object[] original = (Object[])actions.get(event);
        if (original.length == 0 || !singleLetter(data.get(original[0]))) return false;
        Object[] replacement = (Object[])Array.newInstance(actionType, original.length);
        for (int i = 0; i < original.length; i++) {
            Object text = data.get(original[i]);
            replacement[i] = singleLetter(text) ? newAction(code.getInt(original[i]), text.toString().toUpperCase(Locale.ROOT)) : original[i];
        }
        actions.set(event, replacement); return true;
    }
    private Object newAction(int code, String text) throws ReflectiveOperationException {
        return constructor.getParameterTypes().length == 4 ? constructor.newInstance(code,decode,text,0) : constructor.newInstance(code,decode,text);
    }
    boolean uppercaseDecode(Object action) throws IllegalAccessException {
        Object text = data.get(action);
        return intention.get(action) == decode && singleLetter(text)
                && Character.isUpperCase(text.toString().charAt(0));
    }
    static boolean singleLetter(Object text) {
        if (!(text instanceof String) || ((String)text).length() != 1) return false;
        char c = ((String)text).charAt(0); return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
}
