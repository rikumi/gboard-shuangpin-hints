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
        apply(gboard.getPackageManager(), ConfigStore.preferences(gboard, "io.github.rikumi.gboardhints.launcher"), hidden);
    }
    private static int componentFlags() {
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS;
        if (android.os.Build.VERSION.SDK_INT >= 24)
            flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        return flags;
    }
    static void apply(PackageManager pm, SharedPreferences saved, boolean hidden) {
        if (!hidden) {
            for (Map.Entry<String, ?> entry : saved.getAll().entrySet()) {
                ComponentName component = ComponentName.unflattenFromString(entry.getKey());
                if (component == null || !TARGET.equals(component.getPackageName()) || !(entry.getValue() instanceof Integer)) continue;
                try {
                    pm.getActivityInfo(component, componentFlags());
                    int original = (Integer)entry.getValue();
                    if (pm.getComponentEnabledSetting(component) != original)
                        pm.setComponentEnabledSetting(component, original, PackageManager.DONT_KILL_APP);
                } catch (PackageManager.NameNotFoundException ignored) { }
                saved.edit().remove(entry.getKey()).apply();
            }
            return;
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(TARGET);
        java.util.List<ResolveInfo> entries = pm.queryIntentActivities(launcher, componentFlags());
        boolean found = false;
        for (ResolveInfo match : entries) {
            ActivityInfo activity = match.activityInfo;
            if (activity == null || !TARGET.equals(activity.packageName)) continue;
            found = true;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            String key = component.flattenToString();
            int current = pm.getComponentEnabledSetting(component);
            if (!saved.contains(key) && !saved.edit().putInt(key, current).commit())
                throw new IllegalStateException("Cannot preserve launcher state");
            if (current != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        if (!found) throw new IllegalStateException("Gboard launcher entries not available yet");
    }
}
