/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAppearanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun launch(test: (SettingsActivity) -> Unit) {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible()
        try { compose.waitForIdle(); test(controller.get()) }
        finally { controller.pause().stop().destroy() }
    }
    @Test fun couiSwitchesPreserveSettingsAndPublishProviderUpdates() {
        RuntimeEnvironment.getApplication().getSharedPreferences("config", 0).edit()
            .putInt("blank", 2).putString("scheme", "MS").commit()
        launch { activity ->
            val prefs = ConfigStore.prefs(activity)
            assertFalse(prefs.contains("scheme"))
            assertTrue(prefs.getBoolean("blank_keys", false))
            compose.onNodeWithTag("blank_keys").performScrollTo().performClick()
            assertFalse(prefs.getBoolean("blank_keys", true))
            for (key in listOf("initials_only", "single_vowels", "hide_compound_initials", "hide_number_hints", "inline_uppercase", "hide_gboard_launcher")) {
                assertFalse(prefs.getBoolean(key, false))
                compose.onNodeWithTag(key).performScrollTo().performClick()
                assertTrue(prefs.getBoolean(key, false))
            }
            activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("auto", cursor.getString(cursor.getColumnIndexOrThrow("scheme")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("single_vowels")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("hide_gboard_launcher")))
            }
        }
    }
    @Test fun hidingModuleLauncherKeepsSettingsActivityEnabledAndRestoresAlias() = launch { activity ->
        val pm = activity.packageManager
        val main = ComponentName(activity, SettingsActivity::class.java)
        val original = pm.getComponentEnabledSetting(main)
        val toggle = compose.onNodeWithTag("hide_launcher")
        toggle.performScrollTo().performClick()
        assertTrue(ConfigStore.prefs(activity).getBoolean("hide_launcher", false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(LauncherIcon.component(activity)))
        assertEquals(original, pm.getComponentEnabledSetting(main))
        toggle.performClick()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(LauncherIcon.component(activity)))
    }
    @Test fun restartRequiresSelectingTheSingleMenuItem() = launch { activity ->
        var restarts = 0
        activity.setContent { SettingsContent(ConfigStore.prefs(activity), {}, { restarts++ }) }
        compose.onNodeWithText("重启 Gboard").assertDoesNotExist()
        compose.onNodeWithTag("restart_menu").performClick()
        assertEquals(0, restarts)
        compose.onAllNodesWithText("重启 Gboard").assertCountEquals(1)
        compose.onNodeWithText("重启 Gboard").performClick()
        compose.runOnIdle { assertEquals(1, restarts) }
    }
    @Test fun dayModeUsesLightSystemBars() = launch { activity ->
        assertTrue(WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
    }
    @Test @Config(sdk = [31], qualifiers = "night") fun systemNightModeUsesDarkSystemBars() = launch { activity ->
        assertFalse(WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
    }
    @Test fun onlyDeveloperRequestedSubtitlesArePresent() = launch {
        compose.onNodeWithText("初始或等待声母状态下只显示 zh ch sh 的提示，并同时隐藏字母 U I V").assertExists()
        compose.onNodeWithText("需系统启动器支持").assertExists()
        compose.onNodeWithText("在所有提示状态下隐藏 zh、ch、sh。").assertDoesNotExist()
        compose.onNodeWithText("仅在双拼键盘隐藏字母及数字、符号角标，保留功能键。双拼提示由上方开关独立控制。").assertDoesNotExist()
    }
}
