/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import java.util.*;

/** Each independent hook group waits for its real class loader, then installs once. */
final class DeferredHooks {
    interface Install { void run(ClassLoader loader) throws ReflectiveOperationException; }
    interface Failure { void report(String phase, Throwable error); }
    private static final class Stage {
        final String name; final String[] required; final Install install;
        int attempts;
        final Set<Class<?>> installed = Collections.newSetFromMap(new IdentityHashMap<Class<?>,Boolean>());
        Stage(String name, String[] required, Install install) { this.name=name;this.required=required;this.install=install; }
    }
    private final List<Stage> stages=new ArrayList<>();
    private final Failure failure;
    private boolean loading, ready;
    DeferredHooks(Failure failure) { this.failure=failure; }
    void add(String name, String[] required, Install install) { stages.add(new Stage(name,required,install)); }
    synchronized void applicationReady() { ready=true; }
    synchronized void attempt(ClassLoader loader) {
        if(!ready || loader==null || loading)return;
        loading=true;
        try {
            for(Stage stage:stages) try {
                Class<?> identity=Class.forName(stage.required[0],false,loader);
                if(stage.installed.contains(identity))continue;
                stage.attempts++;
                StartupDiagnostics.record("hooks.stage.attempt","stage",stage.name,"attempt",stage.attempts,"loader",StartupDiagnostics.loader(loader));
                for(String name:stage.required) {
                    Class<?> type=Class.forName(name,false,loader);
                    // Resolve signature dependencies before installing any callbacks.
                    type.getDeclaredMethods();type.getDeclaredFields();type.getDeclaredConstructors();
                }
                stage.install.run(loader);stage.installed.add(identity);
                StartupDiagnostics.record("hooks.stage.installed","stage",stage.name,"class",identity.getName(),"loader",StartupDiagnostics.loader(identity.getClassLoader()));
            } catch(ClassNotFoundException missing) {
                StartupDiagnostics.record("hooks.stage.waiting","stage",stage.name,"loader",StartupDiagnostics.loader(loader),"missingClass",missing.getMessage());
                // Split/dynamic features may become available after Application.attach.
            } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
                failure.report(stage.name,error);
            }
        } finally { loading=false; }
    }
    synchronized boolean pending() {
        for(Stage stage:stages)if(stage.installed.isEmpty())return true;
        return false;
    }
}
