/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Runs with Gboard's UID. Only launcher entries of that package are changed. */
final class GboardLauncherIcon {
    private static final String TARGET = "com.google.android.inputmethod.latin";
    static void setHidden(Context gboard, boolean hidden) {
        if (!TARGET.equals(gboard.getPackageName())) throw new IllegalArgumentException("Gboard context required");
        apply(gboard.getPackageManager(), gboard.getSharedPreferences("io.github.rikumi.gboardhints.launcher", 0), hidden);
    }
    static void apply(PackageManager pm, SharedPreferences saved, boolean hidden) {
        if (!hidden) {
            for (Map.Entry<String, ?> entry : saved.getAll().entrySet()) {
                ComponentName component = ComponentName.unflattenFromString(entry.getKey());
                if (component == null || !TARGET.equals(component.getPackageName()) || !(entry.getValue() instanceof Integer)) continue;
                try {
                    pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS);
                    int original = (Integer)entry.getValue();
                    if (pm.getComponentEnabledSetting(component) != original)
                        pm.setComponentEnabledSetting(component, original, PackageManager.DONT_KILL_APP);
                } catch (PackageManager.NameNotFoundException ignored) { }
                saved.edit().remove(entry.getKey()).apply();
            }
            return;
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(TARGET);
        for (ResolveInfo match : pm.queryIntentActivities(launcher, PackageManager.MATCH_DISABLED_COMPONENTS)) {
            ActivityInfo activity = match.activityInfo;
            if (activity == null || !TARGET.equals(activity.packageName)) continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            String key = component.flattenToString();
            int current = pm.getComponentEnabledSetting(component);
            if (!saved.contains(key) && !saved.edit().putInt(key, current).commit())
                throw new IllegalStateException("Cannot preserve launcher state");
            if (current != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
