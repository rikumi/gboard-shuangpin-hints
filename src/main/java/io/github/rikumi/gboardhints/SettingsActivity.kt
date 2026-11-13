/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.popup.WindowDropdownPopup
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val onChange = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        contentResolver.notifyChange(ConfigProvider.URI, null)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = ConfigStore.prefs(this)
        prefs.edit().remove("scheme").apply()
        StateOptions.migrate(prefs)
        prefs.registerOnSharedPreferenceChangeListener(onChange)
        LauncherIcon.setHidden(this, prefs.getBoolean("hide_launcher", false))
        setContent {
            SettingsContent(prefs, onHideLauncher = { LauncherIcon.setHidden(this, it) }, onRestart = {
                GboardControl.request(this,
                    { Toast.makeText(this, R.string.restart_success, Toast.LENGTH_SHORT).show() },
                    { Toast.makeText(this, R.string.restart_unavailable, Toast.LENGTH_SHORT).show() })
            })
        }
    }
    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(onChange)
        super.onDestroy()
    }
}

@Composable
internal fun SettingsContent(prefs: SharedPreferences, onHideLauncher: (Boolean) -> Unit, onRestart: () -> Unit) {
    val colors = settingsColors()
    val navigation = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    val styles = COUITheme.textStyles
    val textStyles = remember(styles) {
        styles.copy(headline1 = styles.headline1.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            body1 = styles.body1.copy(fontWeight = FontWeight.Normal),
            button = styles.button.copy(fontWeight = FontWeight.Normal))
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigation) {
        COUITheme(colors = colors, textStyles = textStyles) {
            val view = LocalView.current
            val dark = isSystemInDarkTheme()
            SideEffect {
                val activity = view.context as? android.app.Activity
                if (activity != null) WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            Column(Modifier.fillMaxSize().background(COUITheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                SmallTopAppBar(title = stringResource(R.string.app_name), showDivider = false,
                    actions = { RestartMenu(onRestart) })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsCard {
                        SettingSwitch(prefs, "enabled", R.string.hints_title, true)
                        HintSize(prefs)
                    }
                    SettingsGroup(R.string.default_state, "default_group") {
                        SettingSwitch(prefs, "default_letters", R.string.show_key_letters, true)
                        SettingSwitch(prefs, "initials_only", R.string.initials_only_title)
                        SettingSwitch(prefs, "initials_below", R.string.initials_below_title)
                        SettingSwitch(prefs, "hide_compound_initials", R.string.hide_compound_initials_title)
                    }
                    SettingsGroup(R.string.finals_state, "finals_group") {
                        SettingSwitch(prefs, "finals_letters", R.string.show_key_letters, true)
                        SettingSwitch(prefs, "dynamic", R.string.dynamic_title, true)
                        SettingSwitch(prefs, "single_vowels", R.string.single_vowels_title)
                    }
                    SettingsGroup(R.string.global_settings, "global_group") {
                        SettingSwitch(prefs, "hide_number_hints", R.string.hide_number_hints_title)
                        SettingSwitch(prefs, "inline_uppercase", R.string.inline_uppercase_title)
                        SettingSwitch(prefs, "hide_launcher", R.string.hide_launcher_title, onChange = onHideLauncher)
                        SettingSwitch(prefs, "hide_gboard_launcher", R.string.hide_gboard_launcher_title,
                            summary = R.string.hide_gboard_launcher_summary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun settingsColors(): Colors {
    val dark = isSystemInDarkTheme()
    val dynamic = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    return if (dark) remember {
        darkColorScheme(primary = Color.White, onPrimary = Color.Black,
            primaryVariant = Color.White.copy(alpha = .16f), onPrimaryVariant = Color.White,
            disabledPrimary = Color.White.copy(alpha = .3f),
            disabledPrimaryButton = Color.White.copy(alpha = .3f),
            primaryContainer = Color.White, onPrimaryContainer = Color.Black,
            tertiaryContainer = Color.White.copy(alpha = .16f), onTertiaryContainer = Color.White,
            tertiaryContainerVariant = Color.White.copy(alpha = .16f),
            background = Color.Black, surface = Color.Black,
            sliderKeyPointForeground = Color.White)
    } else dynamic.currentColors()
}

@Composable
private fun SettingsGroup(title: Int, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.testTag(tag)) {
        SmallTitle(stringResource(title))
        SettingsCard(content)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp, content = content)
}

@Composable
private fun SettingSwitch(prefs: SharedPreferences, key: String, title: Int, fallback: Boolean = false,
                          summary: Int? = null, onChange: (Boolean) -> Unit = {}) {
    var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, fallback)) }
    SwitchPreference(checked = checked, onCheckedChange = { next ->
        onChange(next)
        checked = next
        prefs.edit().putBoolean(key, next).apply()
    }, title = stringResource(title), summary = summary?.let { stringResource(it) },
        modifier = Modifier.testTag(key))
}

@Composable
private fun HintSize(prefs: SharedPreferences) {
    var value by remember { mutableFloatStateOf(prefs.getInt("size", 10).coerceIn(7, 17).toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.size_label, value.roundToInt()), style = COUITheme.textStyles.headline1)
        Slider(value = value, onValueChange = {
            value = it.roundToInt().toFloat()
            prefs.edit().putInt("size", value.toInt()).apply()
        }, valueRange = 7f..17f, steps = 9, modifier = Modifier.testTag("hint_size"))
    }
}

private val RestartIcon = ImageVector.Builder("Restart", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
        moveTo(17.65f, 6.35f); curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f); curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f); lineTo(17.65f, 14f)
        curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f); curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f); curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
        lineTo(13f, 11f); lineTo(20f, 11f); lineTo(20f, 4f); close()
    }
}.build()

@Composable
private fun RestartMenu(onRestart: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.restart_title)
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("restart_menu")) {
            Icon(imageVector = RestartIcon, contentDescription = title)
        }
        WindowDropdownPopup(entry = DropdownEntry(listOf(DropdownItem(text = title, onClick = {
            expanded = false
            onRestart()
        }))), show = expanded, onDismiss = { expanded = false }, onDismissFinished = {}, maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors())
    }
}
