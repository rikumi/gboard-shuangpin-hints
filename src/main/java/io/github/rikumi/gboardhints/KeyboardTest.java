/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;
import java.util.List;
import java.util.Locale;

/** Uses Gboard's own IME token to select an enabled Simplified Chinese keyboard. */
final class KeyboardTest {
    static final String GBOARD = "com.google.android.inputmethod.latin";
    private KeyboardTest() { }
    static boolean isSimplifiedPinyin(InputMethodSubtype subtype) {
        if (!"keyboard".equals(subtype.getMode()) || subtype.isAuxiliary()) return false;
        String tag = Build.VERSION.SDK_INT >= 24 ? subtype.getLanguageTag() : "";
        String locale = (tag.isEmpty() ? subtype.getLocale() : tag).replace('_', '-').toLowerCase(Locale.ROOT);
        if (!(locale.equals("zh") || locale.startsWith("zh-cn") || locale.startsWith("zh-sg") || locale.startsWith("zh-hans"))) return false;
        String extra = subtype.getExtraValue().toLowerCase(Locale.ROOT);
        for (String other : new String[]{"handwriting", "stroke", "wubi", "zhuyin", "cangjie", "bopomofo", "voice"})
            if (extra.contains(other)) return false;
        return true;
    }
    static InputMethodSubtype preferredSubtype(List<InputMethodSubtype> subtypes) {
        InputMethodSubtype fallback = null;
        for (InputMethodSubtype subtype : subtypes) if (isSimplifiedPinyin(subtype)) {
            if (subtype.getExtraValue().toLowerCase(Locale.ROOT).contains("pinyin")) return subtype;
            if (fallback == null) fallback = subtype;
        }
        return fallback;
    }
    static boolean switchToPinyin(InputMethodService service) {
        try {
            InputMethodManager imm = (InputMethodManager)service.getSystemService(Context.INPUT_METHOD_SERVICE);
            String current = Settings.Secure.getString(service.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (!GBOARD.equals(info.getPackageName()) || !info.getId().equals(current)) continue;
                InputMethodSubtype subtype = preferredSubtype(imm.getEnabledInputMethodSubtypeList(info, true));
                if (subtype == null) return false;
                if (Build.VERSION.SDK_INT >= 28) service.switchInputMethod(info.getId(), subtype);
                else imm.setInputMethodAndSubtype(service.getWindow().getWindow().getAttributes().token, info.getId(), subtype);
                return true;
            }
        } catch (RuntimeException e) { HintsModule.logFailure("self-test keyboard", e); }
        return false;
    }
    static void open(EditText editor) {
        editor.post(() -> {
            if (!editor.isAttachedToWindow()) return;
            editor.requestFocus();
            if (!editor.hasFocus()) return;
            InputMethodManager imm = (InputMethodManager)editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            // Give the focused editor time to bind/start Gboard and its receiver.
            editor.postDelayed(() -> request(editor, 0), 250);
        });
    }
    private static void request(EditText editor, int attempt) {
        if (!editor.isAttachedToWindow() || !editor.hasFocus()) return;
        Context context = editor.getContext();
        String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        android.content.ComponentName component = current == null ? null : android.content.ComponentName.unflattenFromString(current);
        if (component == null || !GBOARD.equals(component.getPackageName())) return;
        GboardControl.requestKeyboard(context, () -> {
            if (!editor.isAttachedToWindow() || !editor.hasFocus()) return;
            if (attempt < 2) editor.postDelayed(() -> request(editor, attempt + 1), 350);
        });
    }
}
