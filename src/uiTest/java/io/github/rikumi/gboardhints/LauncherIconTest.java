/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class LauncherIconTest {
    @After public void restore() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("config", 0).edit().remove("hide_launcher").commit();
        LauncherIcon.setHidden(app, false);
    }
    @Test public void switchHidesOnlyAliasAndCanRestoreIt() {
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        try {
            SettingsActivity activity = controller.get(); PackageManager pm = activity.getPackageManager();
            ComponentName main = new ComponentName(activity, SettingsActivity.class);
            int mainState = pm.getComponentEnabledSetting(main);
            MaterialSwitch toggle = activity.findViewById(R.id.hide_launcher_switch);
            assertFalse(toggle.isChecked()); toggle.performClick();
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(LauncherIcon.component(activity)));
            assertEquals(mainState, pm.getComponentEnabledSetting(main));
            assertTrue(activity.getSharedPreferences("config", 0).getBoolean("hide_launcher", false));
            assertFalse(activity.isFinishing()); toggle.performClick();
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(LauncherIcon.component(activity)));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void moduleSettingsEntryRemainsResolvableAfterHidingLauncher() {
        Context app = RuntimeEnvironment.getApplication(); LauncherIcon.setHidden(app, true);
        Intent settings = new Intent(Intent.ACTION_MAIN).addCategory("de.robv.android.xposed.category.MODULE_SETTINGS").setPackage(app.getPackageName());
        boolean found = false;
        for (android.content.pm.ResolveInfo info : app.getPackageManager().queryIntentActivities(settings, 0))
            if (SettingsActivity.class.getName().equals(info.activityInfo.name)) found = true;
        assertTrue(found);
        assertNull(app.getPackageManager().getLaunchIntentForPackage(app.getPackageName()));
    }
}
