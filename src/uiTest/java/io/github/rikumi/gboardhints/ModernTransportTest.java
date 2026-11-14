/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import android.content.*;
import android.database.Cursor;
import android.os.Looper;
import java.lang.reflect.Member;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import io.github.rikumi.gboardhints.bridge.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ModernTransportTest {
    @After public void clear(){XposedBridge.modern=null;XposedBridge.moduleApk=null;}
    private void install(SharedPreferences prefs) {install(prefs,true);}
    private void install(SharedPreferences prefs,boolean remoteSupported) {
        XposedBridge.modern=new XposedBridge.Backend() {
            public boolean supportsRemotePreferences(){return remoteSupported;}
            public int getApiVersion(){return 102;}
            public void log(int level,String tag,String text){}
            public void hook(Member member,XC_MethodHook callback){throw new AssertionError();}
            public boolean deoptimize(Member member){return true;}
            public SharedPreferences getRemotePreferences(String group){assertEquals("config",group);return prefs;}
        };
    }
    private Context invisibleModuleContext() {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName(){return HintsModule.TARGET;}
            @Override public ContentResolver getContentResolver(){throw new AssertionError("Provider transport must not be used");}
            @Override public void sendOrderedBroadcast(Intent intent,String permission,BroadcastReceiver receiver,
                    android.os.Handler scheduler,int code,String data,android.os.Bundle extras){throw new AssertionError("No access broadcast");}
        };
    }
    @Test public void coldStartLoadsAndObservesSettingsWithoutProviderVisibility() {
        Context app=RuntimeEnvironment.getApplication();
        SharedPreferences local=app.getSharedPreferences("source-test",0),remote=app.getSharedPreferences("remote-test",0);
        local.edit().putInt("default_visibility",2).putBoolean("key_letter_size_enabled",true)
                .putInt("key_letter_size",31).putBoolean("hint_size_enabled",true).putInt("size",14).commit();
        ModuleApplication.copy(local,remote);install(remote);
        HintController controller=new HintController();controller.initialize(invisibleModuleContext());
        try {
            assertEquals(14,(int)ReflectionHelpers.getField(controller,"size"));
            assertEquals(2,(int)ReflectionHelpers.getField(controller,"defaultVisibility"));
            assertEquals(31,(int)ReflectionHelpers.getField(controller,"keyLetterSize"));
            assertTrue(ReflectionHelpers.getField(controller,"keyLetterSizeEnabled"));
            local.edit().putInt("size",11).putBoolean("hide_number_hints",true).commit();
            ModuleApplication.copy(local,remote);org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(11,(int)ReflectionHelpers.getField(controller,"size"));
            assertTrue(ReflectionHelpers.getField(controller,"hideNumberHints"));
        }finally{controller.detach();}
    }
    @Test public void firstPublicationImmediatelyReplacesMissingSnapshot() {
        Context app=RuntimeEnvironment.getApplication();
        SharedPreferences local=app.getSharedPreferences("source-empty",0),remote=app.getSharedPreferences("remote-empty",0);
        install(remote);HintController controller=new HintController();controller.initialize(invisibleModuleContext());
        try {
            assertEquals(0L,(long)ReflectionHelpers.getField(controller,"diagnosticConfigGeneration"));
            local.edit().putBoolean("hide_number_hints",true).commit();ModuleApplication.copy(local,remote);
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(ReflectionHelpers.getField(controller,"hideNumberHints"));
        }finally{controller.detach();}
    }
    @Test public void snapshotUsesExactlyTheSameSchemaAsProviderAndRemovesStaleKeys() {
        Context app=RuntimeEnvironment.getApplication();
        SharedPreferences local=app.getSharedPreferences("schema-source",0),remote=app.getSharedPreferences("schema-remote",0);
        local.edit().putBoolean("hide_number_hints",true).putInt("finals_visibility",1).putInt("hint_range",2).commit();
        remote.edit().putString("stale","old").commit();ModuleApplication.copy(local,remote);
        assertFalse(remote.contains("stale"));assertTrue(remote.getBoolean("_snapshot_ready",false));
        try(Cursor expected=ConfigProvider.snapshot(local);Cursor actual=ConfigProvider.snapshot(remote)) {
            assertTrue(expected.moveToFirst());assertTrue(actual.moveToFirst());
            assertArrayEquals(expected.getColumnNames(),actual.getColumnNames());
            for(int i=0;i<expected.getColumnCount();i++)assertEquals(expected.getString(i),actual.getString(i));
        }
    }
    @Test @Config(sdk=23) public void legacySettingsStillStartBelowModernServiceMinimum() {
        assertNotNull(RuntimeEnvironment.getApplication());
        assertFalse(RuntimeEnvironment.getApplication().getResources().getBoolean(R.bool.modern_xposed_service));
        assertNull(XposedBridge.modern);
    }
    @Test public void dictionaryCanLoadFromModuleApkWithoutProviderAccess() throws Exception {
        java.io.File apk=java.io.File.createTempFile("module-assets",".apk");
        try {
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(apk))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("assets/emoji/Ziranma.tsv"));
                zip.write("💩\tui\tui\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            XposedBridge.moduleApk=apk.getPath();
            java.util.Map<String,EmojiLexicon> cache=ReflectionHelpers.getStaticField(EmojiLexicon.class,"cache");cache.clear();
            try {
                EmojiLexicon.request(invisibleModuleContext(),"Ziranma",null);
                java.util.concurrent.ExecutorService loader=ReflectionHelpers.getStaticField(EmojiLexicon.class,"loader");
                loader.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
                EmojiLexicon dictionary=EmojiLexicon.request(invisibleModuleContext(),"Ziranma",null);
                assertNotNull(dictionary);assertEquals("💩",dictionary.match("ui").get(0));
            }finally{cache.clear();}
        }finally{apk.delete();}
    }

    @Test public void embeddedModernFrameworkFallsBackToProviderAndRegistersItsObserver() {
        Context app=RuntimeEnvironment.getApplication();
        install(null,false);
        ColdStartTest.ColdProvider provider=Robolectric.buildContentProvider(ColdStartTest.ColdProvider.class).create().get();
        provider.available=true;provider.hideLauncher=false;
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(ConfigProvider.URI.getAuthority(),provider);
        Context context=new ContextWrapper(app){@Override public String getPackageName(){return HintsModule.TARGET;}};
        HintController controller=new HintController();controller.initialize(context);
        try {
            assertTrue(ReflectionHelpers.getField(controller,"blankEnabled"));
            assertTrue(ReflectionHelpers.getField(controller,"observerRegistered"));
            assertNull(ReflectionHelpers.getField(controller,"remotePreferences"));
        }finally{controller.detach();}
    }

}
