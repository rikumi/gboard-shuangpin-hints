/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import io.github.libxposed.api.XposedModule;
import io.github.rikumi.gboardhints.bridge.XposedBridge;
public final class ModernEntry extends XposedModule {
    private String processName;
    private boolean installed;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName=param.getProcessName();
        XposedBridge.modern=new ModernBackend(this);
        XposedBridge.moduleApk=getModuleApplicationInfo().sourceDir;
        StartupDiagnostics.record("module.entry","api","modern","remotePreferences",XposedBridge.modern.supportsRemotePreferences(),
                "framework",getFrameworkName());
    }
    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if(!HintsModule.TARGET.equals(param.getPackageName()) || installed)return;
        installed=true;
        try {new HintsModule().start(param.getPackageName(),processName,param.getDefaultClassLoader());}
        catch(Throwable error){StartupDiagnostics.failure("modern.install",error);log(android.util.Log.ERROR,"GboardHints","Module installation failed",error);}
    }
}
