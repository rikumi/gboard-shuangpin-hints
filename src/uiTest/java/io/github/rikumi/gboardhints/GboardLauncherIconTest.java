/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.content.pm.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class GboardLauncherIconTest {
    private static final String TARGET = "com.google.android.inputmethod.latin";
    private PackageManager pm; private SharedPreferences saved;
    private ComponentName launcher = new ComponentName(TARGET, "com.google.android.libraries.inputmethod.launcher.LauncherActivity");
    private ComponentName settings = new ComponentName(TARGET, "Settings");
    private ComponentName ime = new ComponentName(TARGET, "Ime");
    @Before public void setup() {
        Context app = RuntimeEnvironment.getApplication(); pm = app.getPackageManager(); saved = app.getSharedPreferences("launcher-test", 0);
        PackageInfo info = new PackageInfo(); info.packageName = TARGET;
        info.applicationInfo = new ApplicationInfo(); info.applicationInfo.packageName = TARGET;
        ActivityInfo main = new ActivityInfo(); main.packageName = TARGET; main.name = launcher.getClassName(); main.enabled = true;
        ActivityInfo config = new ActivityInfo(); config.packageName = TARGET; config.name = settings.getClassName(); config.enabled = true;
        ServiceInfo service = new ServiceInfo(); service.packageName = TARGET; service.name = ime.getClassName(); service.enabled = true;
        info.activities = new ActivityInfo[]{main, config}; info.services = new ServiceInfo[]{service};
        shadowOf(pm).installPackage(info);
        ResolveInfo resolved = new ResolveInfo(); resolved.activityInfo = main;
        shadowOf(pm).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(TARGET), resolved);
    }
    @Test public void hidesAndRestoresOnlyLaunchersWithoutDisablingImeOrSettings() {
        GboardLauncherIcon.apply(pm, saved, true);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(launcher));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(settings));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(ime));
        GboardLauncherIcon.apply(pm, saved, true);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, saved.getInt(launcher.flattenToString(), -1));
        GboardLauncherIcon.apply(pm, saved, false);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(launcher));
        assertTrue(saved.getAll().isEmpty());
    }
    @Test public void disabledDefaultOptionPreservesGboardsOwnExistingHiddenState() {
        pm.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        GboardLauncherIcon.apply(pm, saved, false);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(launcher));
        assertTrue(saved.getAll().isEmpty());
    }
    @Test public void moduleRemovalOfHidingRestoresExplicitEnabledState() {
        pm.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        GboardLauncherIcon.apply(pm, saved, true);
        GboardLauncherIcon.apply(pm, saved, false);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getComponentEnabledSetting(launcher));
    }
}
