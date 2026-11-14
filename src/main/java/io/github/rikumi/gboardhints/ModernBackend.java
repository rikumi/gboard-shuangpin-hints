/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import java.lang.reflect.*;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import io.github.rikumi.gboardhints.bridge.*;
final class ModernBackend implements XposedBridge.Backend {
    private final XposedInterface framework;
    ModernBackend(XposedInterface framework){this.framework=framework;}
    public boolean supportsRemotePreferences(){return (framework.getFrameworkProperties() & XposedInterface.PROP_CAP_REMOTE)!=0;}
    public int getApiVersion(){return framework.getApiVersion();}
    public void log(int level,String tag,String text){framework.log(level,tag,text);}
    public boolean deoptimize(Member member){return framework.deoptimize((Executable)member);}
    public SharedPreferences getRemotePreferences(String group){return framework.getRemotePreferences(group);}
    public void hook(Member member,XC_MethodHook callback){framework.hook((Executable)member).intercept(chain->invoke(callback,member,chain));}
    static Object invoke(XC_MethodHook callback,Member member,XposedInterface.Chain chain) throws Throwable {
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();
        p.method=member;p.thisObject=chain.getThisObject();p.args=chain.getArgs().toArray();
        callback.before(p);
        if(!p.early) {
            try {p.original(chain.proceed(p.args),null);}catch(Throwable error){p.original(null,error);}
        }
        callback.after(p);
        if(p.hasThrowable())throw p.getThrowable();
        return p.getResult();
    }
}
