/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

/** Shared outer/decoder entry policy; directly exercised without Xposed stubs. */
final class InlineUppercaseDispatcher {
    private final InlineUppercaseAdapter adapter;
    private final HintController controller;
    InlineUppercaseDispatcher(InlineUppercaseAdapter adapter, HintController controller) {
        this.adapter = adapter; this.controller = controller;
    }
    boolean handle(Object event, boolean chineseDecoder) throws ReflectiveOperationException {
        if (chineseDecoder) controller.inlineDecoderObserved(true);
        if (!controller.inlineUppercaseActive() || !chineseDecoder || !adapter.press(event)) return false;
        if (adapter.separator(event)) {
            controller.toggleInlineUppercase(); return true;
        }
        if (controller.inlineUppercaseArmed() && adapter.uppercase(event)) controller.cancelInlineUppercase();
        else {
            int code = adapter.keyCode(event);
            if (code == 66 || code == 62 || code == 67) controller.cancelInlineUppercase();
        }
        return false;
    }
    boolean acceptsUppercase(Object action, boolean chineseDecoder) throws IllegalAccessException {
        return chineseDecoder && controller.inlineUppercaseActive() && adapter.uppercaseDecode(action);
    }
}
