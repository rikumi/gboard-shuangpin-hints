/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.os.UserManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StateOptionsTest {
    @Test public void legacyBlankAndInitialsOnlyMigrateWithoutLosingSelections() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        p.edit().putBoolean("blank_keys", true).putBoolean("initials_only", true).commit();
        StateOptions.migrate(p);
        assertFalse(p.getBoolean("default_letters", true));
        assertFalse(p.getBoolean("finals_letters", true));
        assertTrue(p.getBoolean("initials_below", false));
        p.edit().putBoolean("finals_letters", true).commit();
        StateOptions.migrate(p);
        assertTrue(p.getBoolean("finals_letters", false));
        assertFalse(p.getBoolean("default_letters", true));
    }
    @Test public void oldBlankWithMatchingSingleVowelsKeepsTheNativeVowelFilter() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        p.edit().putInt("blank", 2).putBoolean("single_vowels", true).commit();
        StateOptions.migrate(p);
        assertFalse(StateOptions.defaultLetters(p)); assertTrue(StateOptions.finalsLetters(p));
        assertFalse(StateOptions.initialsBelow(p));
    }
    @Test public void lockedProviderReadsCannotFreezeDefaultsBeforeLegacyMigration() {
        Context app = RuntimeEnvironment.getApplication();
        UserManager users = (UserManager)app.getSystemService(Context.USER_SERVICE);
        app.getSharedPreferences("config",0).edit().putBoolean("blank_keys",true).commit();
        shadowOf(users).setUserUnlocked(false);
        SharedPreferences p = ConfigStore.prefs(app);
        assertTrue(StateOptions.defaultLetters(p)); assertFalse(p.contains("default_letters"));
        shadowOf(users).setUserUnlocked(true);
        p = ConfigStore.prefs(app);
        assertFalse(StateOptions.defaultLetters(p));
    }
}
