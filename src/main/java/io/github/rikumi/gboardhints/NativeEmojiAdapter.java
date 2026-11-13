/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.lang.reflect.*;
import java.util.*;

/** Finds the native candidate publication, emoji type and normal HMM commit/reset operation. */
final class NativeEmojiAdapter {
    final Class<?> candidateType;
    final Method publication, commit;
    private final Constructor<?> constructor, builder;
    private final Field kind;
    private final Object emoji;
    private final List<Field> fields = new ArrayList<>();
    NativeEmojiAdapter(Class<?> base, Class<?> hmm) throws ReflectiveOperationException {
        Set<Class<?>> candidates = new HashSet<>();
        for (Method method:base.getDeclaredMethods()) {
            Class<?>[] args=method.getParameterTypes();
            if (args.length!=2 || args[1]!=boolean.class || method.getReturnType()!=boolean.class) continue;
            for (Field field:args[0].getDeclaredFields()) if (field.getType().isEnum() && enumValue(field.getType(),"EMOJI")!=null)
                candidates.add(args[0]);
        }
        if (candidates.size()!=1) throw new NoSuchFieldException("Native candidate type");
        candidateType=candidates.iterator().next();
        Constructor<?> nativeConstructor=null, nativeBuilder=null;
        for (Constructor<?> ctor:candidateType.getDeclaredConstructors()) if (ctor.getParameterTypes().length==1) {
            Constructor<?> empty;
            try { empty=ctor.getParameterTypes()[0].getDeclaredConstructor(); }
            catch(NoSuchMethodException ignored) { continue; }
            if (nativeConstructor!=null) throw new NoSuchMethodException("Ambiguous candidate builder");
            nativeConstructor=ctor; nativeBuilder=empty;
        }
        if(nativeConstructor==null) throw new NoSuchMethodException("Native candidate builder");
        constructor=nativeConstructor;builder=nativeBuilder;constructor.setAccessible(true);builder.setAccessible(true);
        Field nativeKind=null; Object nativeEmoji=null;
        for(Field field:candidateType.getDeclaredFields()) if(!Modifier.isStatic(field.getModifiers())) {
            field.setAccessible(true);fields.add(field);
            if(enumValue(field.getType(),"EMOJI")!=null) { nativeKind=field;nativeEmoji=enumValue(field.getType(),"EMOJI"); }
        }
        kind=nativeKind;emoji=nativeEmoji;
        Set<Method> feeds=new HashSet<>();
        for(Method method:base.getDeclaredMethods()) for(Class<?> param:method.getParameterTypes()) {
            if(param.isPrimitive() || param.isArray() || param.getName().startsWith("java.") || param.getName().startsWith("android.")) continue;
            for(Method factory:param.getDeclaredMethods()) {
                Class<?>[] a=factory.getParameterTypes();
                if(Modifier.isStatic(factory.getModifiers()) && factory.getReturnType()==param && a.length==4
                        && List.class.isAssignableFrom(a[0]) && a[1]==candidateType && a[2]==boolean.class && a[3].isAssignableFrom(base)) feeds.add(factory);
            }
        }
        if(feeds.size()!=1) throw new NoSuchMethodException("Native candidate publication");
        publication=feeds.iterator().next();publication.setAccessible(true);
        Method nativeCommit=null;
        for(Method method:hmm.getDeclaredMethods()) if(!Modifier.isStatic(method.getModifiers()) && method.getReturnType()==void.class
                && Arrays.equals(method.getParameterTypes(),new Class<?>[]{String.class,int.class,boolean.class})) {
            if(nativeCommit!=null) throw new NoSuchMethodException("Ambiguous HMM commit");nativeCommit=method;
        }
        if(nativeCommit==null) throw new NoSuchMethodException("Native HMM commit");commit=nativeCommit;commit.setAccessible(true);
    }
    String text(Object candidate) throws IllegalAccessException {
        Field display=displayField(candidate); Object value=display.get(candidate);return value==null?"":value.toString();
    }
    private Field displayField(Object candidate) throws IllegalAccessException {
        String description=candidate.toString();
        String marker="Candidate : text = '"; int start=description.indexOf(marker);
        String printed=null;
        if(start>=0) { start+=marker.length();int end=description.indexOf("' : rank",start);if(end>=start) printed=description.substring(start,end); }
        Field fallback=null;
        for(Field field:fields) if(field.getType()==CharSequence.class) {
            Object value=field.get(candidate);
            if(printed!=null && value!=null && printed.contentEquals((CharSequence)value)) return field;
            if(fallback==null && value!=null && ((CharSequence)value).length()>0) fallback=field;
        }
        if(fallback!=null)return fallback;
        for(Field field:fields) if(field.getType()==CharSequence.class)return field;
        throw new IllegalAccessException("Candidate text");
    }
    Object emoji(Object source,String value) throws ReflectiveOperationException {
        Object copy=constructor.newInstance(builder.newInstance());Field display=displayField(source);
        for(Field field:fields) {
            Object original=field.get(source);
            if(field.getType()==CharSequence.class) field.set(copy,field==display ? value : null);
            else if(field.getType()==String.class)field.set(copy,value);
            else field.set(copy,original);
        }
        kind.set(copy,emoji);return copy;
    }
    void commit(Object decoder,String text) throws ReflectiveOperationException { commit.invoke(decoder,text,1,true); }
    private static Object enumValue(Class<?> type,String name) {
        if(type.isEnum())for(Object value:type.getEnumConstants())if(((Enum<?>)value).name().equals(name))return value;
        return null;
    }
}
