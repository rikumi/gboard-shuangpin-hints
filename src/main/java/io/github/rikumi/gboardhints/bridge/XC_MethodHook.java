/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints.bridge;
import java.lang.reflect.Member;
/** Module-owned callback contract, independent of either framework API. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam p) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam p) throws Throwable { }
    public final void before(MethodHookParam p) throws Throwable { beforeHookedMethod(p); }
    public final void after(MethodHookParam p) throws Throwable { afterHookedMethod(p); }
    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public boolean early;
        public Object getResult() { return result; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public void setResult(Object result) { this.result=result;throwable=null;early=true; }
        public void setThrowable(Throwable error) { throwable=error;result=null;early=true; }
        public void original(Object result,Throwable error) { this.result=result;throwable=error;early=false; }
    }
}
