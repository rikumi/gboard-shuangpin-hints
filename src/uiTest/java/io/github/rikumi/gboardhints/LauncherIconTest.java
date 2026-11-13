/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
        ConfigStore.prefs(app).edit().remove("hide_launcher").commit();
        LauncherIcon.setHidden(app, false);
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
