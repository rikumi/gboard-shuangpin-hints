/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.TypedValue;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SettingsAppearanceTest {
    @Test public void material3DayThemeAndSingleBlankToggle() {
        RuntimeEnvironment.getApplication().getSharedPreferences("config", 0).edit().putInt("blank", 2).putString("scheme", "MS").commit();
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        try {
            SettingsActivity activity = controller.get();
            verifyTheme(activity, false);
            assertFalse(ConfigStore.prefs(activity).contains("scheme"));
            MaterialSwitch blank = activity.findViewById(R.id.blank_switch);
            assertTrue(blank.isChecked());
            blank.performClick();
            assertFalse(ConfigStore.prefs(activity).getBoolean("blank_keys", true));
            assertTrue(((MaterialSwitch)activity.findViewById(R.id.enabled_switch)).isChecked());
            assertEquals(10, switches(activity.findViewById(R.id.settings_root)));
            for (int id : new int[]{R.id.initials_only_switch, R.id.single_vowels_switch, R.id.hide_compound_initials_switch, R.id.hide_number_hints_switch, R.id.inline_uppercase_switch, R.id.hide_gboard_launcher_switch}) {
                MaterialSwitch option = activity.findViewById(id);
                assertFalse(option.isChecked()); option.performClick();
            }
            try (android.database.Cursor cursor = activity.getContentResolver().query(ConfigProvider.URI, null, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("auto", cursor.getString(cursor.getColumnIndexOrThrow("scheme")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("initials_only")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("single_vowels")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("hide_compound_initials")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("hide_number_hints")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("inline_uppercase")));
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("hide_gboard_launcher")));
            }
        } finally { controller.pause().stop().destroy(); }
    }
    @Test @Config(qualifiers = "night") public void material3FollowsSystemNightMode() {
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        try { verifyTheme(controller.get(), true); }
        finally { controller.pause().stop().destroy(); }
    }
    @Test @Config(sdk = 31) public void monetDayUsesSystemAccent() { verifyMonet(false); }
    @Test @Config(sdk = 31, qualifiers = "night") public void monetNightUsesSystemAccent() { verifyMonet(true); }
    private void verifyMonet(boolean night) {
        ReflectionHelpers.setStaticField(Build.class, "MANUFACTURER", "Google");
        assertTrue(DynamicColors.isDynamicColorAvailable());
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        try {
            SettingsActivity activity = controller.get();
            verifyTheme(activity, night);
            assertEquals(activity.getColor(night ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600),
                    color(activity, androidx.appcompat.R.attr.colorPrimary));
        } finally { controller.pause().stop().destroy(); }
    }
    private void verifyTheme(SettingsActivity activity, boolean night) {
        android.widget.TextView button = activity.findViewById(R.id.restart_button);
        assertEquals(400, button.getTypeface().getWeight());
        assertFalse(button.getTypeface().isBold());
        assertFalse(button.getPaint().isFakeBoldText());
        TypedValue material = new TypedValue();
        assertTrue(activity.getTheme().resolveAttribute(com.google.android.material.R.attr.isMaterial3Theme, material, true));
        assertNotEquals(0, material.data);
        assertEquals(night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO,
                activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
        double luminance = ColorUtils.calculateLuminance(color(activity, com.google.android.material.R.attr.colorSurface));
        assertTrue(night ? luminance < .2 : luminance > .8);
    }
    private int color(SettingsActivity activity, int attribute) {
        TypedArray values = activity.obtainStyledAttributes(new int[]{attribute});
        try { return values.getColorStateList(0).getDefaultColor(); }
        finally { values.recycle(); }
    }
    private int switches(android.view.View view) {
        int count = view instanceof MaterialSwitch ? 1 : 0;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) count += switches(group.getChildAt(i));
        }
        return count;
    }
}
