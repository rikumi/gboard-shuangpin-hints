/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;

/** Reads the active decoder's actual preference backend, including Gboard's in-memory overrides. */
final class NativeScheme {
    private static final class Access {
        final Field preferences;final Method read;
        Access(Field preferences,Method read){this.preferences=preferences;this.read=read;}
    }
    private static final Map<Class<?>,List<Access>> cache=new HashMap<>();
    private static synchronized List<Access> discover(Class<?> decoder) {
        List<Access> result=cache.get(decoder);if(result!=null)return result;
        result=new ArrayList<>();
        for(Class<?> type=decoder;type!=null && type!=Object.class;type=type.getSuperclass())
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive())continue;
                boolean preferences=false;Method read=null;
                for(Method method:field.getType().getMethods()) {
                    if(Modifier.isStatic(method.getModifiers()))continue;
                    if(method.getReturnType()==SharedPreferences.class && method.getParameterTypes().length==0)preferences=true;
                    if(method.getReturnType()==String.class && Arrays.equals(method.getParameterTypes(),new Class<?>[]{String.class,String.class}))read=method;
                }
                if(preferences && read!=null){field.setAccessible(true);read.setAccessible(true);result.add(new Access(field,read));}
            }
        StartupDiagnostics.changed("scheme.backend",decoder.getName()+":"+result.size(),"decoderClass",decoder.getName(),"backendCount",result.size());
        cache.put(decoder,result);return result;
    }
    static String read(Object decoder) throws ReflectiveOperationException {
        for(Access access:discover(decoder.getClass())) {
            Object preferences=access.preferences.get(decoder);if(preferences==null)continue;
            String value=(String)access.read.invoke(preferences,"pinyin_scheme", "\u0000");
            if(value!=null && !value.equals("\u0000")) {
                StartupDiagnostics.changed("scheme.effective",decoder.getClass().getName()+":"+value,
                        "decoderClass",decoder.getClass().getName(),"backendClass",preferences.getClass().getName(),
                        "reader",access.read.getName(),"value",value,"mapped",HintEngine.scheme(value));
                return value;
            }
        }
        return null;
    }
}
