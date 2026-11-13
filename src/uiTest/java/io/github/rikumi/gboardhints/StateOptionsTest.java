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
        StateOptions.setFinalsVisibility(p, StateOptions.LETTERS_ALL);
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
    @Test public void rangeMigrationCombinesLegacySwitchesAndDefaultsAllPlacementOff() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        for (boolean only : new boolean[]{false, true}) for (boolean hide : new boolean[]{false, true}) {
            p.edit().clear().putBoolean("initials_only", only).putBoolean("hide_compound_initials", hide).commit();
            StateOptions.migrate(p);
            int expected = hide ? StateOptions.HIDE_INITIALS : only ? StateOptions.INITIALS_ONLY : StateOptions.ALL;
            assertEquals(expected, StateOptions.hintRange(p));
            assertEquals(expected != StateOptions.ALL, StateOptions.initialsBelow(p));
            assertEquals(expected == StateOptions.INITIALS_ONLY, p.getBoolean("initials_only", false));
            assertEquals(expected == StateOptions.HIDE_INITIALS, p.getBoolean("hide_compound_initials", false));
        }
    }
    @Test public void forcedLowerPlacementDoesNotOverwriteAllModePreference() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        StateOptions.migrate(p);
        assertFalse(StateOptions.initialsBelow(p));
        StateOptions.setHintRange(p, StateOptions.INITIALS_ONLY);
        assertTrue(StateOptions.initialsBelow(p));
        StateOptions.setHintRange(p, StateOptions.HIDE_INITIALS);
        assertTrue(StateOptions.initialsBelow(p));
        StateOptions.setHintRange(p, StateOptions.ALL);
        assertFalse(StateOptions.initialsBelow(p));
        StateOptions.setAllInitialsBelow(p, true);
        StateOptions.setHintRange(p, StateOptions.INITIALS_ONLY);
        StateOptions.setHintRange(p, StateOptions.ALL);
        assertTrue(StateOptions.initialsBelow(p));
    }
    @Test public void explicitLegacyAllModeLowerPlacementIsRetained() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        p.edit().putBoolean("initials_below", true).commit();
        StateOptions.migrate(p);
        assertEquals(StateOptions.ALL, StateOptions.hintRange(p));
        assertTrue(StateOptions.initialsBelow(p));
    }

    @Test public void waitingLetterMenuMigratesLegacyVisibilityAndSingleVowelFlags() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        for (boolean visible : new boolean[]{false, true}) for (boolean single : new boolean[]{false, true}) {
            p.edit().clear().putBoolean("finals_letters", visible).putBoolean("single_vowels", single).commit();
            int expected = !visible ? StateOptions.LETTERS_HIDDEN : single ? StateOptions.LETTERS_AVAILABLE : StateOptions.LETTERS_ALL;
            StateOptions.migrate(p);
            assertEquals(expected, StateOptions.finalsVisibility(p));
            assertEquals(expected != StateOptions.LETTERS_HIDDEN, StateOptions.finalsLetters(p));
            assertEquals(expected == StateOptions.LETTERS_AVAILABLE, StateOptions.singleVowels(p));
        }
    }
    @Test public void waitingLetterMenuChangesKeepDefaultLetterSelectionIndependent() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        p.edit().putBoolean("default_letters", false).commit();
        for (int mode : new int[]{StateOptions.LETTERS_ALL, StateOptions.LETTERS_AVAILABLE, StateOptions.LETTERS_HIDDEN}) {
            StateOptions.setFinalsVisibility(p, mode);
            StateOptions.migrate(p);
            assertEquals(mode, StateOptions.finalsVisibility(p));
            assertFalse(StateOptions.defaultLetters(p));
            assertEquals(mode != StateOptions.LETTERS_HIDDEN, p.getBoolean("finals_letters", false));
            assertEquals(mode == StateOptions.LETTERS_AVAILABLE, p.getBoolean("single_vowels", false));
        }
    }

    @Test public void defaultLetterMenuMigratesOldHiddenAndInitialOnlyModes() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        for (boolean visible : new boolean[]{false, true}) for (boolean only : new boolean[]{false, true}) {
            p.edit().clear().putBoolean("default_letters", visible).putBoolean("initials_only", only).commit();
            StateOptions.migrate(p);
            assertEquals(!visible ? StateOptions.DEFAULT_HIDDEN : only ? StateOptions.DEFAULT_HIDE_VOWELS : StateOptions.DEFAULT_ALL,
                    StateOptions.defaultVisibility(p));
        }
    }
    @Test public void defaultLetterChoicesRemainIndependentOfHintsAndWaitingLetterModes() {
        SharedPreferences p = ConfigStore.prefs(RuntimeEnvironment.getApplication());
        StateOptions.migrate(p);
        for (int mode = StateOptions.DEFAULT_ALL; mode <= StateOptions.DEFAULT_HIDDEN; mode++) {
            StateOptions.setDefaultVisibility(p, mode);
            StateOptions.setHintRange(p, StateOptions.INITIALS_ONLY);
            StateOptions.setFinalsVisibility(p, StateOptions.LETTERS_AVAILABLE);
            StateOptions.migrate(p);
            assertEquals(mode, StateOptions.defaultVisibility(p));
            assertEquals(mode != StateOptions.DEFAULT_HIDDEN, StateOptions.defaultLetters(p));
            assertEquals(StateOptions.LETTERS_AVAILABLE, StateOptions.finalsVisibility(p));
        }
    }

}
