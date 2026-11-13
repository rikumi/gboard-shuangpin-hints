/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.view.View;
import java.lang.reflect.*;
import java.util.*;

/** Gboard 18 metadata uses immutable lists and constructor-only definitions. */
final class ModernNativeKeyAdapter {
    private final Field groups, labels, phase, actions, data;
    private final Shape definitionShape, bindingShape;
    private final Constructor<?> action;
    private final Object decode, commit;
    ModernNativeKeyAdapter(Class<?> definition) throws ReflectiveOperationException {
        definitionShape = new Shape(definition,10);
        groups = definitionShape.fields.get(3); labels = definitionShape.fields.get(4);
        Class<?> binding = Class.forName("com.google.android.libraries.inputmethod.metadata.ActionDef", false, definition.getClassLoader());
        bindingShape = new Shape(binding,14);
        phase = bindingShape.fields.get(0); actions = bindingShape.fields.get(1);
        if (!List.class.isAssignableFrom(groups.getType()) || !List.class.isAssignableFrom(labels.getType())
                || !List.class.isAssignableFrom(actions.getType()) || value(phase.getType(),"PRESS") == null)
            throw new NoSuchFieldException("Immutable native key lists");
        Constructor<?> found = null; Object d = null, c = null;
        for (Method m : binding.getDeclaredMethods()) {
            Class<?> type = m.getReturnType();
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 4 && p[0] == int.class && p[1].isEnum() && p[2] == Object.class && p[3] == int.class
                        && value(p[1],"DECODE") != null && value(p[1],"COMMIT") != null) {
                    found = ctor; d=value(p[1],"DECODE"); c=value(p[1],"COMMIT");
                }
            }
        }
        if (found == null) throw new NoSuchMethodException("Native four-argument action");
        action=found; action.setAccessible(true); decode=d; commit=c;
        Field text=null;
        for(Field f:action.getDeclaringClass().getFields()) if(!Modifier.isStatic(f.getModifiers()) && f.getType()==Object.class) text=f;
        if(text==null) throw new NoSuchFieldException("Native action text"); data=text;
    }
    Object key(Object source, Object actionTemplate, View template, int id, String label, String text, boolean isDecode) throws ReflectiveOperationException {
        Object press=null;
        for(Object group:(List<?>)groups.get(source)) if(phase.get(group)==value(phase.getType(),"PRESS")) { press=group;break; }
        if(press==null)for(Object group:(List<?>)groups.get(actionTemplate))
            if(phase.get(group)==value(phase.getType(),"PRESS")){press=group;break;}
        if(press==null) throw new NoSuchFieldException("Native PRESS binding");
        Map<Integer,Object> changes=new HashMap<>();
        Object event=action.newInstance(text.equals("'")?75:74,isDecode?decode:commit,text,0);
        changes.put(1,list(actions.getType(),event));
        changes.put(11,list(actions.getType(),text)); // accessibility labels
        changes.put(10,text);
        Object binding=bindingShape.construct(press,changes);
        changes.clear(); changes.put(3,list(groups.getType(),binding));
        if(label!=null){changes.put(4,list(labels.getType(),label));changes.put(8,label);changes.put(9,label);}
        for(int index:new int[]{1,2}) {
            int previous=definitionShape.fields.get(index).getInt(source);
            try { if("id".equals(template.getResources().getResourceTypeName(previous))) changes.put(index,id); }
            catch(android.content.res.Resources.NotFoundException ignored) { }
        }
        return definitionShape.construct(source,changes);
    }
    Object normalFace(Object source,Object model) throws ReflectiveOperationException {
        Map<Integer,Object> changes=new HashMap<>();
        changes.put(0,definitionShape.fields.get(0).get(model));
        changes.put(2,definitionShape.fields.get(2).get(model));
        return definitionShape.construct(source,changes);
    }
    String pressText(Object definition) throws ReflectiveOperationException {
        if(definition==null)return "";
        for(Object group:(List<?>)groups.get(definition)) if(phase.get(group)==value(phase.getType(),"PRESS")) {
            List<?> events=(List<?>)actions.get(group);return events.isEmpty()?"":String.valueOf(data.get(events.get(0)));
        }
        return "";
    }
    /** Validate the ordered immutable state against its complete constructor before copying. */
    private static final class Shape {
        final List<Field> fields=new ArrayList<>();
        final Constructor<?> constructor;
        Shape(Class<?> type,int count) throws ReflectiveOperationException {
            for(Field f:type.getDeclaredFields()) if(Modifier.isPublic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())) fields.add(f);
            Collections.sort(fields,(a,b)->a.getName().compareTo(b.getName()));
            if(fields.size()!=count)throw new NoSuchFieldException("Immutable metadata state");
            Class<?>[] signature=new Class<?>[count];
            for(int i=0;i<count;i++){fields.get(i).setAccessible(true);signature[i]=fields.get(i).getType();}
            constructor=type.getDeclaredConstructor(signature);constructor.setAccessible(true);
        }
        Object construct(Object source,Map<Integer,Object> changes) throws ReflectiveOperationException {
            Object[] args=new Object[fields.size()];
            for(int i=0;i<args.length;i++)args[i]=changes.containsKey(i)?changes.get(i):fields.get(i).get(source);
            return constructor.newInstance(args);
        }
    }
    private static Object list(Class<?> type,Object item) throws ReflectiveOperationException {
        for(Method m:type.getDeclaredMethods()) if(Modifier.isStatic(m.getModifiers()) && m.getReturnType()==type
                && Arrays.equals(m.getParameterTypes(),new Class<?>[]{Object[].class})) {
            try {
                m.setAccessible(true);Object result=m.invoke(null,(Object)new Object[]{item});
                if(result instanceof List && ((List<?>)result).size()==1 && ((List<?>)result).get(0)==item)return result;
            } catch(InvocationTargetException ignored) { }
        }
        throw new NoSuchMethodException("Immutable list copyOf");
    }
    private static Object value(Class<?> type,String name) {
        if(type.isEnum())for(Object e:type.getEnumConstants())if(((Enum<?>)e).name().equals(name))return e;
        return null;
    }
}
