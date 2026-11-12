/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherIcon {
    static ComponentName component(Context context) {
        return new ComponentName(context.getPackageName(), context.getPackageName() + ".LauncherAlias");
    }
    static void setHidden(Context context, boolean hidden) {
        int state = hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        ComponentName component = component(context);
        if (context.getPackageManager().getComponentEnabledSetting(component) != state)
            context.getPackageManager().setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
    }
}
