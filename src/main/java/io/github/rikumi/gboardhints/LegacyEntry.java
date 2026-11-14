/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public final class LegacyEntry implements IXposedHookLoadPackage,IXposedHookZygoteInit {
    @Override public void initZygote(StartupParam param) {
        io.github.rikumi.gboardhints.bridge.XposedBridge.moduleApk=param.modulePath;
    }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        StartupDiagnostics.record("module.entry","api","legacy");
        new HintsModule().start(param.packageName,param.processName,param.classLoader);
    }
}
