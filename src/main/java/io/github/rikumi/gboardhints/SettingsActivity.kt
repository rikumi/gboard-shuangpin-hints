/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
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
import io.github.suqi8.coui.kmp.preference.WindowDropdownPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
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
        prefs.edit().remove("scheme").remove("inline_uppercase").apply()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(prefs: SharedPreferences, onHideLauncher: (Boolean) -> Unit, onRestart: () -> Unit,
                             imeVisible: Boolean = WindowInsets.isImeVisible) {
    val colors = settingsColors()
    var testing by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<android.widget.EditText?>(null) }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    fun closeTest() {
        editor?.let {
            (it.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        editor = null
        testing = false
        keyboardWasVisible = false
    }
    BackHandler(enabled = testing) { closeTest() }
    LaunchedEffect(testing, imeVisible) {
        if (testing) {
            if (imeVisible) keyboardWasVisible = true
            else if (keyboardWasVisible) closeTest()
        }
    }
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
                    actions = {
                        Row {
                            IconButton(onClick = {
                                testing = true
                                editor?.let { KeyboardTest.open(it) }
                            }, modifier = Modifier.size(48.dp).testTag("keyboard_test")) {
                                Icon(imageVector = MiuixIcons.Edit, contentDescription = stringResource(R.string.keyboard_test_title),
                                    tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp).testTag("keyboard_icon"))
                            }
                            RestartMenu(onRestart)
                        }
                    })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsCard {
                        SettingSwitch(prefs, "enabled", R.string.hints_title, true)
                        HintSize(prefs)
                        KeyLetterSize(prefs)
                    }
                    SettingsGroup(R.string.default_state, "default_group") {
                        DefaultVisibility(prefs)
                        HintRange(prefs)
                    }
                    SettingsGroup(R.string.finals_state, "finals_group") {
                        FinalsVisibility(prefs)
                        SettingSwitch(prefs, "dynamic", R.string.dynamic_title, true)
                    }
                    SettingsGroup(R.string.global_settings, "global_group") {
                        SettingSwitch(prefs, "hide_number_hints", R.string.hide_number_hints_title)
                        SettingSwitch(prefs, "hide_launcher", R.string.hide_launcher_title, onChange = onHideLauncher)
                        SettingSwitch(prefs, "hide_gboard_launcher", R.string.hide_gboard_launcher_title,
                            summary = R.string.hide_gboard_launcher_summary)
                    }
                }
                if (testing) {
                    AndroidView(factory = { context ->
                        android.widget.EditText(context).apply {
                            minLines = 1
                            maxLines = 3
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            contentDescription = context.getString(R.string.keyboard_test_title)
                            editor = this
                            KeyboardTest.open(this)
                        }
                    }, update = {
                        it.setTextColor(android.graphics.Color.argb(255, (colors.onSurface.red * 255).toInt(),
                            (colors.onSurface.green * 255).toInt(), (colors.onSurface.blue * 255).toInt()))
                        it.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(255,
                            (colors.primary.red * 255).toInt(), (colors.primary.green * 255).toInt(), (colors.primary.blue * 255).toInt()))
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding().testTag("test_editor"))
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
        // The parent already supplies the 16dp page margin.
        SmallTitle(stringResource(title), insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
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
private fun HintRange(prefs: SharedPreferences) {
    var selected by remember { mutableIntStateOf(StateOptions.hintRange(prefs)) }
    WindowDropdownPreference(
        items = listOf(stringResource(R.string.hint_range_all), stringResource(R.string.hint_range_initials),
            stringResource(R.string.hint_range_hide_initials)),
        selectedIndex = selected, title = stringResource(R.string.hint_range_title),
        modifier = Modifier.testTag("hint_range"),
        onSelectedIndexChange = { next ->
            StateOptions.setHintRange(prefs, next)
            selected = next
        })
    if (selected == StateOptions.ALL) {
        SettingSwitch(prefs, "initials_below", R.string.initials_below_title,
            onChange = { StateOptions.setAllInitialsBelow(prefs, it) })
    }
}

@Composable
private fun DefaultVisibility(prefs: SharedPreferences) {
    var selected by remember { mutableIntStateOf(StateOptions.defaultVisibility(prefs)) }
    WindowDropdownPreference(
        items = listOf(stringResource(R.string.hint_range_all), stringResource(R.string.letters_hide_vowels_on_initials),
            stringResource(R.string.letters_hidden)),
        selectedIndex = selected, title = stringResource(R.string.hide_key_letters),
        modifier = Modifier.testTag("default_visibility"),
        onSelectedIndexChange = { next ->
            StateOptions.setDefaultVisibility(prefs, next)
            selected = next
        })
}

@Composable
private fun FinalsVisibility(prefs: SharedPreferences) {
    var selected by remember { mutableIntStateOf(StateOptions.finalsVisibility(prefs)) }
    WindowDropdownPreference(
        items = listOf(stringResource(R.string.hint_range_all), stringResource(R.string.letters_available),
            stringResource(R.string.letters_hidden)),
        selectedIndex = selected, title = stringResource(R.string.hide_key_letters),
        modifier = Modifier.testTag("finals_visibility"),
        onSelectedIndexChange = { next ->
            StateOptions.setFinalsVisibility(prefs, next)
            selected = next
        })
}

@Composable
private fun HintSize(prefs: SharedPreferences) {
    var value by remember { mutableFloatStateOf(prefs.getInt("size", 10).coerceIn(7, 17).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(stringResource(R.string.size_label, value.roundToInt()), style = COUITheme.textStyles.headline1,
            modifier = Modifier.padding(horizontal = 16.dp))
        Slider(value = value, onValueChange = {
            value = it.roundToInt().toFloat()
            prefs.edit().putInt("size", value.toInt()).apply()
        }, valueRange = 7f..17f, steps = 9,
            // COUI's resting track sits 4dp inside its layout bounds.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).testTag("hint_size"))
    }
}

@Composable
private fun KeyLetterSize(prefs: SharedPreferences) {
    var checked by remember { mutableStateOf(prefs.getBoolean("key_letter_size_enabled", false)) }
    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableIntStateOf(prefs.getInt("key_letter_size", PrimaryLetterSize.DEFAULT_SP)
        .coerceIn(PrimaryLetterSize.MIN_SP, PrimaryLetterSize.MAX_SP)) }
    val toggle: (Boolean) -> Unit = { next ->
        checked = next
        expanded = next
        val edit = prefs.edit().putBoolean("key_letter_size_enabled", next)
        if (next) { value = PrimaryLetterSize.DEFAULT_SP; edit.putInt("key_letter_size", value) }
        edit.apply()
    }
    val title = stringResource(R.string.key_letter_size_title)
    Column(Modifier.fillMaxWidth()) {
        if (!checked) {
            SwitchPreference(checked = false, onCheckedChange = toggle, title = title,
                modifier = Modifier.testTag("key_letter_size_row"))
        } else {
            BasicComponent(title = title, onClick = { expanded = !expanded },
                modifier = Modifier.testTag("key_letter_size_row"), endActions = {
                    Text("$value sp", style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceSecondary)
                    VerticalDivider(modifier = Modifier.padding(start = 12.dp).height(24.dp), thickness = 1.dp,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .2f))
                    Switch(checked = true, onCheckedChange = toggle,
                        modifier = Modifier.padding(start = 12.dp).testTag("key_letter_size_switch"))
                })
        }
        AnimatedVisibility(visible = checked && expanded,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Slider(value = value.toFloat(), onValueChange = {
                value = it.roundToInt()
                prefs.edit().putInt("key_letter_size", value).apply()
            }, valueRange = PrimaryLetterSize.MIN_SP.toFloat()..PrimaryLetterSize.MAX_SP.toFloat(),
                steps = PrimaryLetterSize.MAX_SP - PrimaryLetterSize.MIN_SP - 1,
                showKeyPoints = true,
                keyPoints = listOf(PrimaryLetterSize.DEFAULT_SP.toFloat()),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
                    .padding(vertical = 4.dp).testTag("key_letter_size_slider"))
        }
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
    Box(Modifier.size(48.dp)) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.fillMaxSize().testTag("restart_menu")) {
            Icon(imageVector = RestartIcon, contentDescription = title,
                tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp).testTag("restart_icon"))
        }
        WindowDropdownPopup(entry = DropdownEntry(listOf(DropdownItem(text = title, onClick = {
            expanded = false
            onRestart()
        }))), show = expanded, onDismiss = { expanded = false }, onDismissFinished = {}, maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors())
    }
}
