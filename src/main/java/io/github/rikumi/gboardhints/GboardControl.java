/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.content.*;
import android.os.*;

/** A signature-protected request lets the injected Gboard process restart itself. */
final class GboardControl {
    static final String ACTION = "io.github.rikumi.gboardhints.action.RESTART_GBOARD";
    static final String KEYBOARD_ACTION = "io.github.rikumi.gboardhints.action.TEST_KEYBOARD";
    interface KeyboardAction { boolean run(); }
    static final String PERMISSION = "io.github.rikumi.gboardhints.permission.CONTROL_GBOARD";
    static Intent restartIntent() { return new Intent(ACTION).setPackage("com.google.android.inputmethod.latin").addFlags(Intent.FLAG_RECEIVER_FOREGROUND); }
    static BroadcastReceiver register(Context context, Runnable restart) {
        return register(context, restart, () -> false);
    }
    static BroadcastReceiver register(Context context, Runnable restart, KeyboardAction keyboard) {
        BroadcastReceiver receiver = new RestartReceiver(restart, keyboard);
        IntentFilter filter = new IntentFilter(ACTION);
        filter.addAction(KEYBOARD_ACTION);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, PERMISSION, null, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, filter, PERMISSION, null);
        return receiver;
    }
    static void request(Context context, Runnable success, Runnable unavailable) {
        // The receiver requires the sender's signature permission. Requiring a
        // receiver permission here would incorrectly exclude the Gboard UID.
        context.sendOrderedBroadcast(restartIntent(), null, new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) success.run(); else unavailable.run();
            }
        }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null);
    }
    static void requestKeyboard(Context context, Runnable unavailable) {
        Intent intent = new Intent(KEYBOARD_ACTION).setPackage(KeyboardTest.GBOARD)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (getResultCode() != Activity.RESULT_OK) unavailable.run();
            }
        }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null);
    }
    static final class RestartReceiver extends BroadcastReceiver {
        private final Runnable restart;
        private final KeyboardAction keyboard;
        private boolean pending;
        RestartReceiver(Runnable restart, KeyboardAction keyboard) { this.restart = restart; this.keyboard = keyboard; }
        @Override public void onReceive(Context context, Intent intent) {
            if (!isOrderedBroadcast()) return;
            if (KEYBOARD_ACTION.equals(intent.getAction())) {
                if (keyboard.run()) setResultCode(Activity.RESULT_OK);
                return;
            }
            if (!ACTION.equals(intent.getAction())) return;
            setResultCode(Activity.RESULT_OK);
            if (pending) return;
            pending = true;
            new Handler(Looper.getMainLooper()).postDelayed(restart, 250);
        }
    }
}
