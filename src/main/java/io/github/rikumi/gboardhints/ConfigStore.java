/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import java.util.Map;
import java.util.Set;

/** Display settings remain readable during direct boot; input text is never stored. */
final class ConfigStore {
    private static final String MIGRATED = "_device_storage_migrated";
    static SharedPreferences prefs(Context context) { return preferences(context, "config"); }
    static synchronized SharedPreferences preferences(Context context, String name) {
        if (Build.VERSION.SDK_INT < 24 || context.isDeviceProtectedStorage()) return context.getSharedPreferences(name, 0);
        Context device = context.createDeviceProtectedStorageContext();
        SharedPreferences target = device.getSharedPreferences(name, 0);
        UserManager users = (UserManager)context.getSystemService(Context.USER_SERVICE);
        if (!target.getBoolean(MIGRATED, false) && users != null && users.isUserUnlocked()) {
            SharedPreferences source = context.getSharedPreferences(name, 0);
            SharedPreferences.Editor edit = target.edit();
            for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (target.contains(key)) continue;
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
                else if (value instanceof Integer) edit.putInt(key, (Integer)value);
                else if (value instanceof Long) edit.putLong(key, (Long)value);
                else if (value instanceof Float) edit.putFloat(key, (Float)value);
                else if (value instanceof String) edit.putString(key, (String)value);
                else if (value instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> strings = (Set<String>)value;
                    edit.putStringSet(key, strings);
                }
            }
            // Preserve the credential copy for rollback, but never re-import it.
            if (!edit.putBoolean(MIGRATED, true).commit()) throw new IllegalStateException("Configuration migration failed");
        }
        return target;
    }
}
