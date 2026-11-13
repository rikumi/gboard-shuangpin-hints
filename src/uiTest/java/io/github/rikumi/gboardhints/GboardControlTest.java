/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.pm.PermissionInfo;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class GboardControlTest {
    private Application app;
    @Before public void setup() {
        app = RuntimeEnvironment.getApplication(); shadowOf(app).grantPermissions(GboardControl.PERMISSION);
    }
    @Test public void requestTargetsOnlyGboard() {
        Intent intent = GboardControl.restartIntent();
        assertEquals("com.google.android.inputmethod.latin", intent.getPackage());
        assertEquals(GboardControl.ACTION, intent.getAction());
        assertNotEquals(0, intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND);
    }
    @Test public void receiverRequiresTheModuleSignaturePermission() throws Exception {
        PermissionInfo permission = app.getPackageManager().getPermissionInfo(GboardControl.PERMISSION, 0);
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, permission.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
        BroadcastReceiver receiver = GboardControl.register(app, () -> { });
        try {
            boolean found = false;
            for (ShadowApplication.Wrapper registered : shadowOf(app).getRegisteredReceivers()) {
                if (registered.broadcastReceiver == receiver) {
                    assertEquals(GboardControl.PERMISSION, registered.broadcastPermission); found = true;
                }
            }
            assertTrue(found);
        } finally { app.unregisterReceiver(receiver); }
    }
    @Test public void orderedRequestAcknowledgesBeforeRestartAndDebounces() {
        int[] killed = {0}, result = {Activity.RESULT_CANCELED};
        BroadcastReceiver receiver = GboardControl.register(app, () -> killed[0]++);
        try {
            app.sendOrderedBroadcast(GboardControl.restartIntent().setPackage(null), null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { result[0] = getResultCode(); }
            }, null, Activity.RESULT_CANCELED, null, null);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Activity.RESULT_OK, result[0]); assertEquals(0, killed[0]);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertEquals(1, killed[0]);
            app.sendOrderedBroadcast(GboardControl.restartIntent().setPackage(null), null);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            assertEquals(1, killed[0]);
        } finally { app.unregisterReceiver(receiver); }
    }
    @Test public void unorderedBroadcastDoesNotRestart() {
        int[] killed = {0}; BroadcastReceiver receiver = GboardControl.register(app, () -> killed[0]++);
        try {
            app.sendBroadcast(GboardControl.restartIntent().setPackage(null));
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            assertEquals(0, killed[0]);
        } finally { app.unregisterReceiver(receiver); }
    }
    @Test public void missingInjectedReceiverReportsUnavailable() {
        int[] success = {0}, unavailable = {0};
        GboardControl.request(app, () -> success[0]++, () -> unavailable[0]++);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, success[0]); assertEquals(1, unavailable[0]);
    }
    @Test public void keyboardRequestUsesTheSameSignatureGuardAndNeverRestartsGboard() {
        int[] switches = {0}, restarts = {0}, result = {Activity.RESULT_CANCELED};
        BroadcastReceiver receiver = GboardControl.register(app, () -> restarts[0]++, () -> { switches[0]++; return true; });
        try {
            Intent intent = new Intent(GboardControl.KEYBOARD_ACTION);
            app.sendBroadcast(intent);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, switches[0]);
            app.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { result[0] = getResultCode(); }
            }, null, Activity.RESULT_CANCELED, null, null);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            assertEquals(Activity.RESULT_OK, result[0]);
            assertEquals(1, switches[0]); assertEquals(0, restarts[0]);
        } finally { app.unregisterReceiver(receiver); }
    }

}
