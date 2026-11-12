/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.os.UserManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ConfigStoreTest {
    @Test public void existingPreferencesMigrateOnceAndSurviveLockedStartup() {
        Context app = RuntimeEnvironment.getApplication();
        UserManager users = (UserManager)app.getSystemService(Context.USER_SERVICE);
        shadowOf(users).setUserUnlocked(true);
        SharedPreferences old = app.getSharedPreferences("config", 0);
        old.edit().putBoolean("blank_keys", true).putBoolean("hide_gboard_launcher", true).commit();
        SharedPreferences migrated = ConfigStore.prefs(app);
        assertTrue(migrated.getBoolean("blank_keys", false));
        assertTrue(migrated.getBoolean("hide_gboard_launcher", false));
        migrated.edit().putBoolean("blank_keys", false).commit();
        shadowOf(users).setUserUnlocked(false);
        assertFalse(ConfigStore.prefs(app).getBoolean("blank_keys", true));
        assertTrue(ConfigStore.prefs(app).getBoolean("hide_gboard_launcher", false));
        shadowOf(users).setUserUnlocked(true);
        assertFalse(ConfigStore.prefs(app).getBoolean("blank_keys", true));
        assertTrue(old.getBoolean("blank_keys", false));
    }
    @Test public void unlockMergesLegacyValuesWithoutOverwritingDeviceChanges() {
        Context app = RuntimeEnvironment.getApplication();
        UserManager users = (UserManager)app.getSystemService(Context.USER_SERVICE);
        app.getSharedPreferences("config", 0).edit()
                .putBoolean("blank_keys", true).putBoolean("hide_gboard_launcher", true).commit();
        shadowOf(users).setUserUnlocked(false);
        SharedPreferences locked = ConfigStore.prefs(app);
        assertFalse(locked.contains("hide_gboard_launcher"));
        locked.edit().putBoolean("blank_keys", false).commit();
        shadowOf(users).setUserUnlocked(true);
        assertTrue(ConfigStore.prefs(app).getBoolean("hide_gboard_launcher", false));
        assertFalse(ConfigStore.prefs(app).getBoolean("blank_keys", true));
    }
    @Test public void configProviderIsAvailableDuringDirectBoot() {
        Context app = RuntimeEnvironment.getApplication();
        ProviderInfo info = app.getPackageManager().resolveContentProvider(ConfigProvider.URI.getAuthority(), 0);
        assertNotNull(info); assertTrue(info.directBootAware);
    }
    @Test @Config(sdk = 23) public void androidSixKeepsCredentialStorage() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("config", 0).edit().putBoolean("blank_keys", true).commit();
        assertTrue(ConfigStore.prefs(app).getBoolean("blank_keys", false));
        ConfigStore.prefs(app).edit().putBoolean("blank_keys", false).commit();
        assertFalse(app.getSharedPreferences("config", 0).getBoolean("blank_keys", true));
    }
}
