/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.view.inputmethod.InputMethodSubtype;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class KeyboardSelfTestTest {
    private InputMethodSubtype subtype(String locale, String extra) {
        return new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale(locale)
                .setSubtypeMode("keyboard").setSubtypeExtraValue(extra).build();
    }
    @Test public void selectsPinyinAheadOfOtherChineseLayoutsAndEnglish() {
        InputMethodSubtype pinyin = subtype("zh_CN", "KeyboardLayoutSet=pinyin");
        assertSame(pinyin, KeyboardTest.preferredSubtype(Arrays.asList(
                subtype("en_US", ""), subtype("zh_CN", "KeyboardLayoutSet=handwriting"),
                subtype("zh_TW", "KeyboardLayoutSet=zhuyin"), subtype("zh_CN", ""), pinyin)));
        assertNull(KeyboardTest.preferredSubtype(Collections.singletonList(subtype("zh_CN", "KeyboardLayoutSet=wubi"))));
        assertNull(KeyboardTest.preferredSubtype(Collections.emptyList()));
    }
    @Test public void languageTagTakesPrecedenceAndTraditionalChineseIsExcluded() {
        InputMethodSubtype hans = new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale("en_US")
                .setLanguageTag("zh-Hans-CN").setSubtypeMode("keyboard").build();
        assertTrue(KeyboardTest.isSimplifiedPinyin(hans));
        assertFalse(KeyboardTest.isSimplifiedPinyin(subtype("zh_Hant_TW", "")));
        assertFalse(KeyboardTest.isSimplifiedPinyin(subtype("zh_HK", "")));
    }
    @Test @Config(sdk = 23) public void legacySystemsUseLocaleWithoutNewerLanguageTagApi() {
        InputMethodSubtype pinyin = subtype("zh_CN", "");
        assertSame(pinyin, KeyboardTest.preferredSubtype(Collections.singletonList(pinyin)));
    }
    @org.robolectric.annotation.Implements(android.view.inputmethod.InputMethodManager.class)
    public static class PickerShadow extends org.robolectric.shadows.ShadowInputMethodManager {
        static int pickers;
        @org.robolectric.annotation.Implementation protected void showInputMethodPicker() { pickers++; }
    }
    @Test @Config(shadows = PickerShadow.class) public void openingSelfTestNeverOpensAPickerForAnotherImeOrMissingGboardReceiver() {
        org.robolectric.android.controller.ActivityController<android.app.Activity> controller =
                org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().visible();
        try {
            android.app.Activity activity = controller.get();
            android.widget.EditText editor = new android.widget.EditText(activity);
            activity.setContentView(editor);
            PickerShadow.pickers = 0;
            for (String current : new String[]{"other.keyboard/.Service", KeyboardTest.GBOARD + "/.LatinIME"}) {
                android.provider.Settings.Secure.putString(activity.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, current);
                KeyboardTest.open(editor);
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
                assertTrue(editor.hasFocus());
                assertEquals(0, PickerShadow.pickers);
            }
        } finally { controller.pause().stop().destroy(); }
    }

}
