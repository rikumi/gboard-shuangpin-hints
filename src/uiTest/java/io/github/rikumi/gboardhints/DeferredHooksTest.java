/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DeferredHooksTest {
    public static class Settings { }
    public static class Keyboard { }
    public static class Dependency { }
    private static final class LateLoader extends ClassLoader {
        final Set<String> missing=new HashSet<>();
        LateLoader() { super(DeferredHooksTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(missing.contains(name))throw new ClassNotFoundException(name);
            return super.loadClass(name,resolve);
        }
    }
    @Test public void missingKeyboardDoesNotPreventSettingsAndRecoversWhenSplitArrives() {
        LateLoader loader=new LateLoader();loader.missing.add(Keyboard.class.getName());
        int[] installed={0,0};
        DeferredHooks hooks=new DeferredHooks((phase,error)->fail(error.toString()));
        hooks.add("settings",new String[]{Settings.class.getName()},l->installed[0]++);
        hooks.add("keyboard",new String[]{Keyboard.class.getName(),Dependency.class.getName()},l->installed[1]++);
        hooks.applicationReady();hooks.attempt(loader);assertArrayEquals(new int[]{1,0},installed);assertTrue(hooks.pending());
        loader.missing.clear();hooks.attempt(loader);hooks.attempt(loader);
        assertArrayEquals(new int[]{1,1},installed);assertFalse(hooks.pending());
        // A wrapper delegating to the same classes must not duplicate the hooks.
        hooks.attempt(new ClassLoader(loader){});assertArrayEquals(new int[]{1,1},installed);
    }
    @Test public void signatureDependencyAndTransientFailureCanRetryWithoutBlockingOtherGroup() {
        LateLoader loader=new LateLoader();loader.missing.add(Dependency.class.getName());
        int[] attempts={0,0},failures={0};
        DeferredHooks hooks=new DeferredHooks((phase,error)->failures[0]++);
        hooks.add("keyboard",new String[]{Keyboard.class.getName(),Dependency.class.getName()},l->{
            if(attempts[0]++==0)throw new NoClassDefFoundError("late dependency");
        });
        hooks.add("settings",new String[]{Settings.class.getName()},l->attempts[1]++);
        hooks.applicationReady();hooks.attempt(loader);assertArrayEquals(new int[]{0,1},attempts);
        loader.missing.clear();hooks.attempt(loader);assertTrue(hooks.pending());
        hooks.attempt(loader);hooks.attempt(loader);
        assertArrayEquals(new int[]{2,1},attempts);assertEquals(1,failures[0]);assertFalse(hooks.pending());
    }
    @Test public void recursiveClassResolutionCannotInstallTheSameGroupTwice() {
        int[] count={0};DeferredHooks[] holder=new DeferredHooks[1];
        holder[0]=new DeferredHooks((phase,error)->fail(error.toString()));
        holder[0].add("keyboard",new String[]{Keyboard.class.getName()},l->{count[0]++;holder[0].attempt(l);});
        holder[0].applicationReady();holder[0].attempt(getClass().getClassLoader());assertEquals(1,count[0]);assertFalse(holder[0].pending());
    }
    @Test public void noNativeClassIsResolvedBeforeApplicationOnCreateCompletes() {
        int[] resolutions={0},installations={0};
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                resolutions[0]++;return super.loadClass(name,resolve);
            }
        };
        DeferredHooks hooks=new DeferredHooks((phase,error)->fail(error.toString()));
        hooks.add("keyboard",new String[]{Keyboard.class.getName()},l->installations[0]++);
        hooks.attempt(loader);assertEquals(0,resolutions[0]);assertEquals(0,installations[0]);
        hooks.applicationReady();hooks.attempt(loader);
        assertTrue(resolutions[0]>0);assertEquals(1,installations[0]);
    }

}
