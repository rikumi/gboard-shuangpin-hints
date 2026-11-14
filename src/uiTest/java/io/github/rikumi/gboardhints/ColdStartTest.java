/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Looper;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ColdStartTest {
    public static final class ColdProvider extends ContentProvider {
        int attempts; boolean available, hideLauncher = true;
        Runnable onFirstQuery;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            attempts++;
            boolean ready = available;
            if (attempts == 1 && onFirstQuery != null) onFirstQuery.run();
            if (!ready) return null;
            MatrixCursor result = new MatrixCursor(new String[]{"enabled", "scheme", "size", "dynamic", "blank", "initials_only", "single_vowels", "hide_compound_initials", "hide_number_hints", "inline_uppercase", "hide_gboard_launcher"});
            result.addRow(new Object[]{1, "auto", 10, 1, 1, 0, 0, 0, 0, 0, hideLauncher ? 1 : 0});
            return result;
        }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    }
    private static final class Ime extends InputMethodService {
        Ime(Context app) { attachBaseContext(app); }
        @Override public String getPackageName() { return HintsModule.TARGET; }
    }
    @Test public void unavailableFirstQueryRecoversBothOptionsWithoutReopeningSettings() {
        Context app = RuntimeEnvironment.getApplication();
        ColdProvider provider = Robolectric.buildContentProvider(ColdProvider.class).create().get();
        ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(), provider);
        PackageManager pm = app.getPackageManager();
        PackageInfo pkg = new PackageInfo(); pkg.packageName = HintsModule.TARGET;
        pkg.applicationInfo = new ApplicationInfo(); pkg.applicationInfo.packageName = pkg.packageName;
        ActivityInfo entry = new ActivityInfo(); entry.packageName = pkg.packageName;
        entry.name = "com.google.android.libraries.inputmethod.launcher.LauncherActivity"; entry.enabled = true;
        pkg.activities = new ActivityInfo[]{entry}; shadowOf(pm).installPackage(pkg);
        ResolveInfo resolved = new ResolveInfo(); resolved.activityInfo = entry;
        shadowOf(pm).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg.packageName), resolved);
        HintController controller = new HintController();
        controller.attach(new Ime(app));
        try {
            assertFalse(ReflectionHelpers.getField(controller, "blankEnabled"));
            assertEquals(1, provider.attempts);
            provider.available = true;
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            assertTrue(ReflectionHelpers.getField(controller, "blankEnabled"));
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    pm.getComponentEnabledSetting(new ComponentName(entry.packageName, entry.name)));
            assertEquals(2, provider.attempts);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
            assertEquals("Successful recovery stops retrying", 2, provider.attempts);
        } finally { controller.detach(); }
    }
    @Test public void detachCancelsPendingStartupRetry() {
        Context app = RuntimeEnvironment.getApplication();
        ColdProvider provider = Robolectric.buildContentProvider(ColdProvider.class).create().get();
        ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(), provider);
        HintController controller = new HintController(); controller.attach(new Ime(app));
        controller.detach();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(1, provider.attempts);
    }
    @Test public void unlockingDuringFirstQueryReloadsPreferencesImmediately() {
        Context app = RuntimeEnvironment.getApplication();
        ColdProvider provider = Robolectric.buildContentProvider(ColdProvider.class).create().get();
        ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(), provider);
        provider.hideLauncher = false;
        provider.onFirstQuery = () -> {
            provider.available = true;
            app.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
        };
        HintController controller = new HintController(); controller.attach(new Ime(app));
        try {
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue("Unlock during the first query cannot be lost", provider.attempts >= 2);
            assertTrue("Only unlock and the optional access-grant callback reload", provider.attempts <= 3);
            assertTrue(ReflectionHelpers.getField(controller, "blankEnabled"));
        } finally { controller.detach(); }
    }
    @Test public void providerStartingAfterRetryWindowStillRecoversWithoutAnotherInputSession() {
        Context app=RuntimeEnvironment.getApplication();
        ColdProvider provider=Robolectric.buildContentProvider(ColdProvider.class).create().get();
        provider.hideLauncher=false;
        ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(),provider);
        HintController controller=new HintController();controller.attach(new Ime(app));
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
            int failedAttempts=provider.attempts;assertTrue(failedAttempts>=7);
            provider.available=true;
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
            assertTrue(ReflectionHelpers.getField(controller,"blankEnabled"));
            assertEquals(failedAttempts+1,provider.attempts);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
            assertEquals(failedAttempts+1,provider.attempts);
        } finally {controller.detach();}
    }
    @Test public void applicationInitializationLoadsSettingsBeforeInputMethodServiceExists() {
        Context app=RuntimeEnvironment.getApplication();
        ColdProvider provider=Robolectric.buildContentProvider(ColdProvider.class).create().get();
        provider.available=true;provider.hideLauncher=false;
        ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(),provider);
        HintController controller=new HintController();
        controller.initialize(new Ime(app));
        try {
            assertNull(ReflectionHelpers.getField(controller,"service"));
            assertTrue(ReflectionHelpers.getField(controller,"blankEnabled"));
            controller.initialize(new Ime(app));assertEquals(1,provider.attempts);
        } finally {controller.detach();}
    }

}
