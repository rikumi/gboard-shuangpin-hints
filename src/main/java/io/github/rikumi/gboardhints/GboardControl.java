/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.content.*;
import android.os.*;

/** A signature-protected request lets the injected Gboard process restart itself. */
final class GboardControl {
    static final String ACTION = "io.github.rikumi.gboardhints.action.RESTART_GBOARD";
    static final String PERMISSION = "io.github.rikumi.gboardhints.permission.CONTROL_GBOARD";
    static Intent restartIntent() { return new Intent(ACTION).setPackage("com.google.android.inputmethod.latin").addFlags(Intent.FLAG_RECEIVER_FOREGROUND); }
    static BroadcastReceiver register(Context context, Runnable restart) {
        BroadcastReceiver receiver = new RestartReceiver(restart);
        IntentFilter filter = new IntentFilter(ACTION);
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
    static final class RestartReceiver extends BroadcastReceiver {
        private final Runnable restart;
        private boolean pending;
        RestartReceiver(Runnable restart) { this.restart = restart; }
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION.equals(intent.getAction()) || !isOrderedBroadcast()) return;
            setResultCode(Activity.RESULT_OK);
            if (pending) return;
            pending = true;
            new Handler(Looper.getMainLooper()).postDelayed(restart, 250);
        }
    }
}
