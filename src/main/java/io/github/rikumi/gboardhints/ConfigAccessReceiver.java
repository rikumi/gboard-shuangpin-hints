/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.net.Uri;

/** Grants only the fixed, read-only module endpoints to the fixed Gboard package. */
public final class ConfigAccessReceiver extends BroadcastReceiver {
    static final String PACKAGE = "io.github.rikumi.gboardhints";
    static final String ACTION = PACKAGE + ".CONFIG_ACCESS";
    static final Uri EMOJI = Uri.parse("content://io.github.rikumi.gboardhints.config/emoji/");
    static void grant(Context context) {
        context.grantUriPermission(HintsModule.TARGET, ConfigProvider.URI,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        context.grantUriPermission(HintsModule.TARGET, EMOJI,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        StartupDiagnostics.record("config.access.granted");
    }
    static void persist(Context context) {
        for (Uri uri : new Uri[]{ConfigProvider.URI, EMOJI}) {
            try { context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (RuntimeException e) { StartupDiagnostics.failure("persist config access",e); }
        }
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        try { grant(context); if (isOrderedBroadcast()) setResultCode(android.app.Activity.RESULT_OK); }
        catch (RuntimeException e) { StartupDiagnostics.failure("config access grant",e); }
    }
}
