/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.database.MatrixCursor;
import android.inputmethodservice.InputMethodService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class StartupDiagnosticsTest {
    @Before public void begin(){Assume.assumeTrue(BuildConfig.DIAGNOSTICS);ShadowLog.clear();}
    private String logs(){return ShadowLog.getLogsForTag(StartupDiagnostics.TAG).stream().map(x->x.msg).collect(Collectors.joining("\n"));}
    @Test public void eachStartupEmitsASeparateVersionedProcessRecord() {
        StartupDiagnostics.begin(HintsModule.TARGET,HintsModule.TARGET,getClass().getClassLoader());
        StartupDiagnostics.begin(HintsModule.TARGET,HintsModule.TARGET,getClass().getClassLoader());
        assertEquals(2,ShadowLog.getLogsForTag(StartupDiagnostics.TAG).size());
        assertTrue(logs().contains("process.start"));assertTrue(logs().contains(BuildConfig.VERSION_NAME));
        assertTrue(logs().contains("\"pid\":"));assertTrue(logs().contains("startup-config-v1"));
    }
    @Test public void storedSettingsAreWhitelistedAndRepeatForEveryQuery() {
        Context context=RuntimeEnvironment.getApplication();
        SharedPreferences prefs=context.getSharedPreferences("diagnostic-test",0);
        prefs.edit().putBoolean("enabled",false).putInt("default_visibility",2)
                .putInt("size",13).putString("typed_text","SECRET_INPUT_934").commit();
        StartupDiagnostics.stored(context,prefs);StartupDiagnostics.stored(context,prefs);
        assertEquals(2,ShadowLog.getLogsForTag(StartupDiagnostics.TAG).size());
        assertTrue(logs().contains("\"enabled\":false"));assertTrue(logs().contains("\"default_visibility\":2"));
        assertTrue(logs().contains("\"size\":13"));assertFalse(logs().contains("SECRET_INPUT_934"));assertFalse(logs().contains("typed_text"));
    }
    @Test public void cursorSnapshotRetainsActualValuesWithoutUnknownColumnsOrFailureMessages() {
        MatrixCursor c=new MatrixCursor(new String[]{"enabled","scheme","inline_uppercase","composition"});
        c.addRow(new Object[]{0,"auto",1,"SECRET_INPUT_934"});assertTrue(c.moveToFirst());
        StartupDiagnostics.cursor(c);StartupDiagnostics.failure("test",new IllegalStateException("SECRET_INPUT_934"));
        assertTrue(logs().contains("\"inline_uppercase\":1"));assertTrue(logs().contains("\"scheme\":\"auto\""));
        assertTrue(logs().contains("java.lang.IllegalStateException"));assertFalse(logs().contains("SECRET_INPUT_934"));
        assertFalse(logs().contains("\"composition\""));
    }
    @Test public void composingTextIsNeverIncludedInControllerDiagnostics() {
        HintController controller=new HintController();
        controller.start(new android.view.inputmethod.EditorInfo());controller.engineComposition("SECRET_INPUT_934");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(logs().contains("SECRET_INPUT_934"));controller.detach();
    }
    @Test public void unavailableConfigurationAndRecoveredEffectiveSnapshotAreBothLogged() {
        Context app=RuntimeEnvironment.getApplication();
        ColdStartTest.ColdProvider provider=Robolectric.buildContentProvider(ColdStartTest.ColdProvider.class).create().get();
        provider.hideLauncher=false;
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(),provider);
        Context gboard=new ContextWrapper(app){@Override public String getPackageName(){return HintsModule.TARGET;}};
        HintController controller=new HintController();controller.initialize(gboard);
        try {
            assertTrue(logs().contains("null_cursor"));assertTrue(logs().contains("\"refreshSuccessful\":false"));
            provider.available=true;
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            assertTrue(logs().contains("config.cursor"));assertTrue(logs().contains("\"refreshSuccessful\":true"));
            assertTrue(logs().contains("\"blankEnabled\":true"));
        } finally {controller.detach();}
    }

}
