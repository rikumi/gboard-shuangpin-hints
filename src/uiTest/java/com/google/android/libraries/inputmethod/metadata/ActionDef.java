/* SPDX-License-Identifier: LGPL-2.1-or-later */
package com.google.android.libraries.inputmethod.metadata;
import java.util.*;
public final class ActionDef {
    public enum Phase { PRESS, LONG_PRESS }
    public enum Intention { DECODE, COMMIT }
    public static final class Event {
        public final int code; public final Intention intention; public final Object data; private int hash;
        public Event(int c,Intention j,Object d,int hash) {code=c;intention=j;data=d;this.hash=hash;}
    }
    public static final class Items extends AbstractList<Object> {
        private final List<Object> values;
        private Items(Object[] values){this.values=Arrays.asList(values);}
        public static Items q(Object[] values){return new Items(values);}
        @Override public Object get(int index){return values.get(index);}
        @Override public int size(){return values.size();}
    }
    public final Phase f; public final Items g; public final boolean h,i; public final int j;
    public final boolean k,l,m; public final int n,o; public final String p; public final Items q;
    public final int[] r; public final boolean s;
    public ActionDef(Phase f,Items g,boolean h,boolean i,int j,boolean k,boolean l,boolean m,int n,int o,String p,Items q,int[] r,boolean s){
        this.f=f;this.g=g;this.h=h;this.i=i;this.j=j;this.k=k;this.l=l;this.m=m;this.n=n;this.o=o;this.p=p;this.q=q;this.r=r;this.s=s;
    }
    public Event event(){return (Event)g.get(0);}
}
