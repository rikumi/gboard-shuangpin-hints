/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 Gboard Shuangpin Hints contributors
 */
package io.github.rikumi.gboardhints;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.snackbar.Snackbar;

public final class SettingsActivity extends AppCompatActivity {
    private final SharedPreferences.OnSharedPreferenceChangeListener onChange = (prefs, key) ->
            getContentResolver().notifyChange(ConfigProvider.URI, null);
    @Override public void onCreate(Bundle state) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        SharedPreferences prefs = getSharedPreferences("config", 0);
        if (prefs.contains("scheme")) prefs.edit().remove("scheme").apply();
        if (!prefs.contains("blank_keys")) prefs.edit().putBoolean("blank_keys", prefs.getInt("blank", 0) != 0).apply();
        prefs.registerOnSharedPreferenceChangeListener(onChange);
        bindSwitch(R.id.enabled_switch, "enabled", true, prefs);
        bindSwitch(R.id.dynamic_switch, "dynamic", true, prefs);
        bindSwitch(R.id.hide_compound_initials_switch, "hide_compound_initials", false, prefs);
        bindSwitch(R.id.initials_only_switch, "initials_only", false, prefs);
        bindSwitch(R.id.single_vowels_switch, "single_vowels", false, prefs);
        bindSwitch(R.id.hide_number_hints_switch, "hide_number_hints", false, prefs);
        bindSwitch(R.id.inline_uppercase_switch, "inline_uppercase", false, prefs);
        bindSwitch(R.id.blank_switch, "blank_keys", false, prefs);
        bindSwitch(R.id.hide_gboard_launcher_switch, "hide_gboard_launcher", false, prefs);
        MaterialSwitch hideLauncher = findViewById(R.id.hide_launcher_switch);
        hideLauncher.setChecked(prefs.getBoolean("hide_launcher", false));
        LauncherIcon.setHidden(this, hideLauncher.isChecked());
        hideLauncher.setOnCheckedChangeListener((button, checked) -> {
            LauncherIcon.setHidden(this, checked);
            prefs.edit().putBoolean("hide_launcher", checked).apply();
        });
        MaterialButton restart = findViewById(R.id.restart_button);
        restart.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        restart.setOnClickListener(view -> {
            restart.setEnabled(false);
            GboardControl.request(this, () -> {
                restart.setEnabled(true);
                Snackbar.make(findViewById(R.id.settings_root), R.string.restart_success, Snackbar.LENGTH_LONG).show();
            }, () -> {
                restart.setEnabled(true);
                Snackbar.make(findViewById(R.id.settings_root), R.string.restart_unavailable, Snackbar.LENGTH_LONG).show();
            });
        });
        MaterialTextView size = findViewById(R.id.size_label);
        Slider slider = findViewById(R.id.size_slider);
        int value = Math.max(7, Math.min(17, prefs.getInt("size", 10)));
        slider.setValue(value);
        size.setText(getString(R.string.size_label, value));
        slider.addOnChangeListener((control, next, fromUser) -> {
            if (fromUser) {
                prefs.edit().putInt("size", (int)next).apply();
                size.setText(getString(R.string.size_label, (int)next));
            }
        });
    }
    private void bindSwitch(int id, String key, boolean fallback, SharedPreferences prefs) {
        MaterialSwitch toggle = findViewById(id);
        toggle.setChecked(prefs.getBoolean(key, fallback));
        toggle.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
    }
    @Override protected void onDestroy() {
        getSharedPreferences("config", 0).unregisterOnSharedPreferenceChangeListener(onChange);
        super.onDestroy();
    }
}
