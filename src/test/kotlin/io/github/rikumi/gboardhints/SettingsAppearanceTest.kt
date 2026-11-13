/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
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
            assertFalse(prefs.getBoolean("default_letters", true))
            assertFalse(prefs.getBoolean("finals_letters", true))
            assertEquals(StateOptions.DEFAULT_HIDDEN, StateOptions.defaultVisibility(prefs))
            assertEquals(StateOptions.LETTERS_HIDDEN, StateOptions.finalsVisibility(prefs))
            compose.onNodeWithTag("default_visibility").performScrollTo().performClick()
            compose.onNode(hasText("全部显示") and hasAnyAncestor(isDialog())).performClick()
            assertTrue(prefs.getBoolean("default_letters", false))
            compose.onNodeWithTag("finals_visibility").performScrollTo().performClick()
            compose.onNode(hasText("只显示可用韵母") and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
            assertTrue(prefs.getBoolean("finals_letters", false))
            assertTrue(prefs.getBoolean("single_vowels", false))
            for (key in listOf("show_semicolon", "flexible_emoji", "inline_uppercase", "hide_number_hints", "hide_gboard_launcher")) {
                assertFalse(prefs.getBoolean(key, false))
                compose.onNodeWithTag(key).performScrollTo().performClick()
                assertTrue(prefs.getBoolean(key, false))
            }
            activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("auto", cursor.getString(cursor.getColumnIndexOrThrow("scheme")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("show_semicolon")))
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
        compose.onNodeWithText("初始或等待声母状态下只显示 zh ch sh 的提示，并同时隐藏字母 U I V").assertDoesNotExist()
        compose.onNodeWithText("需系统启动器支持").assertExists()
        compose.onNodeWithText("适用于不显示独立数字键时的首排字母").assertExists()
        compose.onNodeWithText("在所有提示状态下隐藏 zh、ch、sh。").assertDoesNotExist()
        compose.onNodeWithText("仅在双拼键盘隐藏字母及数字、符号角标，保留功能键。双拼提示由上方开关独立控制。").assertDoesNotExist()
    }
    @Test fun stateGroupsKeepControlsInTheirRequestedSections() = launch {
        for (title in listOf("默认状态下", "等待韵母状态下", "实用功能"))
            compose.onNodeWithText(title).assertExists()
        compose.onAllNodesWithText("隐藏按键主字母").assertCountEquals(2)
        for (tag in listOf("default_visibility", "hint_range", "initials_below"))
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("default_group"))).assertExists()
        compose.onNode(hasTestTag("finals_visibility") and hasAnyAncestor(hasTestTag("finals_group"))).assertExists()
        for (tag in listOf("show_semicolon", "hide_number_hints", "hide_launcher", "hide_gboard_launcher"))
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("global_group"))).assertExists()
        compose.onNode(hasTestTag("key_letter_size_row") and hasAnyAncestor(hasTestTag("global_group"))).assertExists()
        compose.onNodeWithText("无刻模式").assertDoesNotExist()
    }
    @Test @Config(sdk = [31], qualifiers = "night") fun nightPaletteIsPureBlackWithWhiteAccent() = launch { activity ->
        var colors: io.github.suqi8.coui.kmp.theme.Colors? = null
        activity.setContent { colors = settingsColors() }
        compose.runOnIdle {
            assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.background)
            assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.surface)
            assertEquals(androidx.compose.ui.graphics.Color.White, colors!!.primary)
            assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.onPrimary)
        }
    }

    @Test fun hintRangeControlsLowerInitialPlacementAndRemembersAllModeChoice() = launch { activity ->
        val prefs = ConfigStore.prefs(activity)
        val range = compose.onNodeWithTag("hint_range")
        val lower = compose.onNodeWithTag("initials_below")
        assertEquals(StateOptions.ALL, StateOptions.hintRange(prefs))
        lower.assertExists()
        assertFalse(StateOptions.initialsBelow(prefs))
        compose.onNodeWithText("只显示声母提示").assertDoesNotExist()
        compose.onNodeWithText("隐藏复声母").assertDoesNotExist()
        fun select(text: String) {
            range.performScrollTo().performClick()
            compose.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
        }
        select("只显示声母")
        lower.assertDoesNotExist()
        assertTrue(prefs.getBoolean("initials_only", false))
        assertFalse(prefs.getBoolean("hide_compound_initials", true))
        assertTrue(StateOptions.initialsBelow(prefs))
        select("只隐藏声母")
        lower.assertDoesNotExist()
        assertFalse(prefs.getBoolean("initials_only", true))
        assertTrue(prefs.getBoolean("hide_compound_initials", false))
        assertTrue(StateOptions.initialsBelow(prefs))
        select("全部显示")
        lower.assertExists()
        assertFalse(StateOptions.initialsBelow(prefs))
        lower.performScrollTo().performClick()
        assertTrue(StateOptions.initialsBelow(prefs))
        select("只显示声母")
        lower.assertDoesNotExist()
        select("全部显示")
        lower.assertExists()
        assertTrue(StateOptions.initialsBelow(prefs))
        activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("initials_only")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("hide_compound_initials")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("initials_below")))
        }
    }

    @Test fun waitingLetterMenuHasThreeModesAndPublishesTheirNativePolicies() = launch { activity ->
        val prefs = ConfigStore.prefs(activity)
        val menu = compose.onNodeWithTag("finals_visibility")
        compose.onNodeWithText("双拼提示动态显示可用韵母").assertExists()
        compose.onNodeWithText("根据声母显示可用韵母").assertDoesNotExist()
        compose.onNodeWithText("根据声母显示单韵母").assertDoesNotExist()
        for ((label, mode) in listOf("只显示可用韵母" to StateOptions.LETTERS_AVAILABLE,
            "全部隐藏" to StateOptions.LETTERS_HIDDEN, "全部显示" to StateOptions.LETTERS_ALL)) {
            menu.performScrollTo().performClick()
            compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
            assertEquals(mode, StateOptions.finalsVisibility(prefs))
            activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(if (mode == StateOptions.LETTERS_HIDDEN) 0 else 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("finals_letters")))
                assertEquals(if (mode == StateOptions.LETTERS_AVAILABLE) 1 else 0,
                    cursor.getInt(cursor.getColumnIndexOrThrow("single_vowels")))
            }
        }
    }
    @Test fun hintSizeIsOptInDefaultsToTenAndPublishesEffectiveValue() = launch { activity ->
        val prefs = ConfigStore.prefs(activity)
        fun effectiveSize(): Int = activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use {
            assertTrue(it.moveToFirst()); it.getInt(it.getColumnIndexOrThrow("size"))
        }
        val row = compose.onNodeWithTag("hint_size_row")
        val slider = compose.onNodeWithTag("hint_size_slider")
        compose.onNodeWithText("修改提示字号").assertExists()
        assertEquals(10, effectiveSize()); slider.assertDoesNotExist()
        row.performScrollTo().performClick()
        assertTrue(prefs.getBoolean("hint_size_enabled", false)); slider.assertExists()
        assertEquals(10, prefs.getInt("size", 0))
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(14f) }
        assertEquals(14, effectiveSize())
        row.performScrollTo().performClick(); compose.waitForIdle(); slider.assertDoesNotExist()
        assertTrue(prefs.getBoolean("hint_size_enabled", false))
        row.performClick(); compose.waitForIdle(); slider.assertExists()
        compose.onNodeWithTag("hint_size_switch").performClick(); compose.waitForIdle()
        assertEquals(10, effectiveSize()); slider.assertDoesNotExist()
    }

    @Test fun letterSizeSwitchExpandsAndLeftSideOnlyTogglesItsSlider() = launch { activity ->
        val prefs = ConfigStore.prefs(activity)
        val row = compose.onNodeWithTag("key_letter_size_row")
        val slider = compose.onNodeWithTag("key_letter_size_slider")
        slider.assertDoesNotExist()
        row.performScrollTo().performClick()
        assertTrue(prefs.getBoolean("key_letter_size_enabled", false))
        slider.assertExists()
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(30f) }
        assertEquals(30, prefs.getInt("key_letter_size", 0))
        row.performScrollTo().performClick()
        compose.waitForIdle(); slider.assertDoesNotExist()
        assertTrue(prefs.getBoolean("key_letter_size_enabled", false))
        row.performClick(); compose.waitForIdle(); slider.assertExists()
        compose.onNodeWithTag("key_letter_size_switch").performClick()
        compose.waitForIdle(); slider.assertDoesNotExist()
        assertFalse(prefs.getBoolean("key_letter_size_enabled", true))
        row.performClick()
        assertEquals(PrimaryLetterSize.DEFAULT_SP, prefs.getInt("key_letter_size", 0))
        slider.assertExists()
    }

    @Test fun defaultLetterMenuPublishesAllThreeModesIndependentlyOfHintRange() = launch { activity ->
        val prefs = ConfigStore.prefs(activity)
        val menu = compose.onNodeWithTag("default_visibility")
        for ((label, mode) in listOf("隐藏声母键上的韵母" to StateOptions.DEFAULT_HIDE_VOWELS,
            "全部隐藏" to StateOptions.DEFAULT_HIDDEN, "全部显示" to StateOptions.DEFAULT_ALL)) {
            menu.performScrollTo().performClick()
            compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
            assertEquals(mode, StateOptions.defaultVisibility(prefs))
            assertEquals(StateOptions.LETTERS_ALL, StateOptions.finalsVisibility(prefs))
            activity.contentResolver.query(ConfigProvider.URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(mode, cursor.getInt(cursor.getColumnIndexOrThrow("default_visibility")))
                assertEquals(if (mode == StateOptions.DEFAULT_HIDDEN) 0 else 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("default_letters")))
            }
        }
    }

    @Test fun keyboardTestButtonIsLeftOfRestartAndOpensAFocusedEditor() = launch { activity ->
        compose.onNodeWithText("分词键改为行内大写").assertDoesNotExist()
        compose.onNodeWithTag("inline_uppercase").assertExists()
        val keyboard = compose.onNodeWithTag("keyboard_test")
        val restart = compose.onNodeWithTag("restart_menu")
        val keyboardBounds = SettingsSnapshot.bounds(keyboard)
        val restartBounds = SettingsSnapshot.bounds(restart)
        assertTrue("Keyboard button must be left of restart", keyboardBounds[0] + keyboardBounds[2] <= restartBounds[0] + 1f)
        compose.onNodeWithTag("test_editor").assertDoesNotExist()
        keyboard.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("test_editor").assertIsDisplayed()
        val editor = SettingsSnapshot.editor(activity.window.decorView)
        assertNotNull(editor)
        assertTrue(editor!!.hasFocus())
        editor.setText("测试 abc")
        keyboard.performClick()
        compose.waitForIdle()
        assertEquals("测试 abc", editor.text.toString())
    }

    @Test fun hidingTheKeyboardRemovesTheSelfTestEditorAndReopeningStartsFresh() = launch { activity ->
        val ime = mutableStateOf(false)
        activity.setContent { SettingsContent(ConfigStore.prefs(activity), {}, {}, imeVisible = ime.value) }
        val button = compose.onNodeWithTag("keyboard_test")
        button.performClick(); compose.waitForIdle()
        compose.onNodeWithTag("test_editor").assertIsDisplayed()
        val first = SettingsSnapshot.editor(activity.window.decorView)!!
        first.setText("临时测试")
        compose.runOnIdle { ime.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("test_editor").assertIsDisplayed()
        compose.runOnIdle { ime.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("test_editor").assertDoesNotExist()
        assertFalse(first.hasFocus())
        assertNull(SettingsSnapshot.editor(activity.window.decorView))
        button.performClick(); compose.waitForIdle()
        compose.onNodeWithTag("test_editor").assertIsDisplayed()
        assertEquals("", SettingsSnapshot.editor(activity.window.decorView)!!.text.toString())
    }

    private fun verifyRestartVisibility(dark: Boolean) = launch { activity ->
        val button = compose.onNodeWithTag("restart_menu")
        button.assertIsDisplayed()
        val bounds = SettingsSnapshot.bounds(button)
        val root = SettingsSnapshot.bounds(compose.onRoot())
        val density = activity.resources.displayMetrics.density
        assertEquals(48 * density, bounds[2], 1f)
        assertEquals(48 * density, bounds[3], 1f)
        assertTrue("Restart must remain on the right", bounds[0] > root[2] / 2)
        for (tag in listOf("restart_icon", "keyboard_icon")) {
            val pixels = SettingsSnapshot.capture(compose.onNodeWithTag(tag, useUnmergedTree = true))
            var contrasting = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels.getPixel(x,y)
                val red = android.graphics.Color.red(color)
                val green = android.graphics.Color.green(color)
                val blue = android.graphics.Color.blue(color)
                if (android.graphics.Color.alpha(color) > 128 && if (dark) red > 153 && green > 153 && blue > 153
                    else red < 102 && green < 102 && blue < 102) contrasting++
            }
            pixels.recycle()
            assertTrue("$tag must contrast with its background", contrasting > 20)
        }
        button.performClick()
        compose.onNodeWithText("重启 Gboard").assertIsDisplayed()
    }
    @Test fun restartButtonIsVisibleInLightMode() = verifyRestartVisibility(false)
    @Test @Config(sdk = [31], qualifiers = "night") fun restartButtonIsVisibleInDarkMode() = verifyRestartVisibility(true)

}
