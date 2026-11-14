/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.rikumi.gboardhints.bridge.XC_MethodHook;
import org.junit.Test;
import static org.junit.Assert.*;
public class ModernHooksTest {
    private final Method method;
    public ModernHooksTest() throws Exception {method=String.class.getMethod("substring",int.class);}
    private XposedInterface.Chain chain(Object result,Throwable failure,int[] calls) {
        return (XposedInterface.Chain)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{XposedInterface.Chain.class},(object,m,args)-> {
            switch(m.getName()) {
                case "getArgs":return Arrays.asList(1);
                case "getThisObject":return "text";
                case "getExecutable":return method;
                case "proceed":calls[0]++;calls[1]=(Integer)((Object[])args[0])[0];if(failure!=null)throw failure;return result;
                default:throw new AssertionError(m.getName());
            }
        });
    }
    @Test public void changedArgumentsAndNativeResultReachAfterCallback() throws Throwable {
        int[] calls={0,0};
        Object result=ModernBackend.invoke(new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p){assertEquals("text",p.thisObject);p.args[0]=2;}
            @Override protected void afterHookedMethod(MethodHookParam p){assertEquals("native",p.getResult());p.setResult("changed");}
        },method,chain("native",null,calls));
        assertEquals("changed",result);assertArrayEquals(new int[]{1,2},calls);
    }
    @Test public void consumedKeyDoesNotInvokeNativeMethod() throws Throwable {
        int[] calls={0,0};
        Object result=ModernBackend.invoke(new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p){p.setResult("consumed");}
        },method,chain("native",null,calls));
        assertEquals("consumed",result);assertEquals(0,calls[0]);
    }
    @Test public void nativeFailureIsPreservedWithoutRetryingOriginal() throws Throwable {
        int[] calls={0,0};IllegalStateException error=new IllegalStateException();
        try {ModernBackend.invoke(new XC_MethodHook(){},method,chain(null,error,calls));fail();}
        catch(IllegalStateException actual){assertSame(error,actual);}
        assertEquals(1,calls[0]);
    }
    @Test public void afterCallbackCanRecoverNativeFailure() throws Throwable {
        int[] calls={0,0};
        assertEquals("recovered",ModernBackend.invoke(new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p){assertTrue(p.hasThrowable());p.setResult("recovered");}
        },method,chain(null,new IllegalArgumentException(),calls)));
    }
}
