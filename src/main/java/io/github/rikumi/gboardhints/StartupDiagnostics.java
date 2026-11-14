/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.os.*;
import android.util.Log;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Diagnostic builds only. Whitelisted display preferences and structural startup state; never editor/input data. */
final class StartupDiagnostics {
    static final String TAG="GboardHintsDiag";
    private static final AtomicLong sequence=new AtomicLong();
    private static final Set<String> OPTIONS=new LinkedHashSet<>(Arrays.asList(
            "enabled","scheme","hint_size_enabled","size","dynamic","blank","blank_keys","hint_range",
            "initials_only","single_vowels","hide_compound_initials","hide_number_hints","inline_uppercase",
            "hide_gboard_launcher","hide_launcher","default_letters","finals_letters","initials_below",
            "all_initials_below","key_letter_size_enabled","key_letter_size","default_visibility","finals_visibility",
            "show_semicolon","flexible_emoji","_device_storage_migrated"));
    private static final Map<String,String> changes=new HashMap<>();
    private StartupDiagnostics() { }
    static void record(String event,Object... values) {
        if(!BuildConfig.DIAGNOSTICS)return;
        try {
            JSONObject entry=new JSONObject();
            entry.put("diag","startup-config-v1");entry.put("event",event);
            entry.put("pid",android.os.Process.myPid());entry.put("uid",android.os.Process.myUid());
            entry.put("seq",sequence.incrementAndGet());entry.put("elapsedMs",SystemClock.elapsedRealtime());
            for(int i=0;i+1<values.length;i+=2)entry.put((String)values[i],values[i+1]==null?JSONObject.NULL:values[i+1]);
            String line=entry.toString();
            Log.i(TAG,line);
            try {io.github.rikumi.gboardhints.bridge.XposedBridge.log(TAG+": "+line);}
            catch(RuntimeException | LinkageError ignored){ }
        } catch(JSONException | RuntimeException | LinkageError ignored) { }
    }
    static synchronized void changed(String event,String signature,Object... values) {
        if(!BuildConfig.DIAGNOSTICS || signature.equals(changes.get(event)))return;
        changes.put(event,signature);record(event,values);
    }
    static String loader(ClassLoader loader) {
        return loader==null?"bootstrap":loader.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(loader));
    }
    static void begin(String targetPackage,String process,ClassLoader loader) {
        Integer api=null;
        try {api=io.github.rikumi.gboardhints.bridge.XposedBridge.getXposedVersion();}catch(RuntimeException | LinkageError ignored){ }
        record("process.start","xposedApi",api,"bridgeLoader",loader(io.github.rikumi.gboardhints.bridge.XposedBridge.class.getClassLoader()),"package",targetPackage,"process",process,"moduleVersion",BuildConfig.VERSION_NAME,
                "moduleCode",BuildConfig.VERSION_CODE,"sdk",Build.VERSION.SDK_INT,"loader",loader(loader));
    }
    static void context(String phase,Context context) {
        if(!BuildConfig.DIAGNOSTICS)return;
        Boolean unlocked=Build.VERSION.SDK_INT<24 ? Boolean.TRUE : null;
        try {if(Build.VERSION.SDK_INT>=24) {UserManager users=(UserManager)context.getSystemService(Context.USER_SERVICE);if(users!=null)unlocked=users.isUserUnlocked();}}
        catch(RuntimeException ignored){ }
        record(phase,"contextClass",context.getClass().getName(),"package",context.getPackageName(),
                "deviceStorage",Build.VERSION.SDK_INT>=24 && context.isDeviceProtectedStorage(),"userUnlocked",unlocked,
                "loader",loader(context.getClassLoader()));
    }
    static void providerVisibility(Context context) {
        if(!BuildConfig.DIAGNOSTICS)return;
        try {
            PackageManager pm=context.getPackageManager();
            try {
                PackageInfo target=pm.getPackageInfo(HintsModule.TARGET,0);
                record("gboard.installed","version",target.versionName,"code",target.versionCode,
                        "targetSdk",target.applicationInfo==null?null:target.applicationInfo.targetSdkVersion);
            } catch(PackageManager.NameNotFoundException ignored){record("gboard.installed","visible",false);}
            boolean patched=false;
            try(java.io.InputStream ignored=context.getAssets().open("lspatch/config.json")){patched=true;}
            catch(java.io.IOException ignored){ }
            record("deployment","hostHasLspatchConfig",patched);
            ProviderInfo provider=pm.resolveContentProvider(EmojiLexicon.AUTHORITY,
                    Build.VERSION.SDK_INT>=24 ? PackageManager.MATCH_DIRECT_BOOT_AWARE|PackageManager.MATCH_DIRECT_BOOT_UNAWARE : 0);
            record("provider.visibility","visible",provider!=null,"authority",EmojiLexicon.AUTHORITY,
                    "providerClass",provider==null?null:provider.name,"providerPackage",provider==null?null:provider.packageName,
                    "exported",provider!=null && provider.exported,"enabled",provider!=null && provider.enabled,
                    "directBootAware",provider!=null && Build.VERSION.SDK_INT>=24 && provider.directBootAware);
            try {
                PackageInfo module=pm.getPackageInfo(BuildConfig.APPLICATION_ID,0);
                record("module.installed","visible",true,"version",module.versionName,"code",module.versionCode,
                        "matchesInjectedVersion",BuildConfig.VERSION_NAME.equals(module.versionName));
            } catch(PackageManager.NameNotFoundException e){record("module.installed","visible",false);}
        } catch(RuntimeException error){failure("provider.visibility",error);}
    }
    static void stored(Context context,SharedPreferences preferences) {
        if(!BuildConfig.DIAGNOSTICS)return;
        try {
            Map<String,?> all=preferences.getAll();JSONObject selected=new JSONObject();
            for(String key:OPTIONS)selected.put(key,all.containsKey(key)?all.get(key):JSONObject.NULL);
            record("config.provider","callerPid",Binder.getCallingPid(),"callerUid",Binder.getCallingUid(),
                    "moduleVersion",BuildConfig.VERSION_NAME,"moduleCode",BuildConfig.VERSION_CODE,
                    "preferenceStorage",Build.VERSION.SDK_INT>=24 ? "device_protected" : "credential_protected","stored",selected);
        } catch(JSONException | RuntimeException error){failure("config.provider",error);}
    }
    static void cursor(Cursor cursor) {
        if(!BuildConfig.DIAGNOSTICS)return;
        try {
            JSONObject selected=new JSONObject();
            for(String key:OPTIONS) {
                int column=cursor.getColumnIndex(key);if(column<0)continue;
                switch(cursor.getType(column)) {
                    case Cursor.FIELD_TYPE_NULL:selected.put(key,JSONObject.NULL);break;
                    case Cursor.FIELD_TYPE_INTEGER:selected.put(key,cursor.getLong(column));break;
                    case Cursor.FIELD_TYPE_FLOAT:selected.put(key,cursor.getDouble(column));break;
                    case Cursor.FIELD_TYPE_STRING:selected.put(key,cursor.getString(column));break;
                    default:selected.put(key,"<unsupported type>");
                }
            }
            record("config.cursor","columnCount",cursor.getColumnCount(),"received",selected);
        } catch(JSONException | RuntimeException error){failure("config.cursor",error);}
    }
    static void failure(String phase,Throwable error) {
        if(!BuildConfig.DIAGNOSTICS)return;
        try {
            JSONArray causes=new JSONArray(),frames=new JSONArray();Throwable current=error;
            for(int i=0;i<4 && current!=null;i++,current=current.getCause()) {
                causes.put(current.getClass().getName());
                StackTraceElement[] stack=current.getStackTrace();
                for(int j=0;j<Math.min(4,stack.length) && frames.length()<12;j++)frames.put(stack[j].toString());
            }
            // Throwable messages can contain arbitrary text; only types and code locations are emitted.
            record("failure","phase",phase,"causes",causes,"frames",frames);
        } catch(RuntimeException ignored){ }
    }
}
