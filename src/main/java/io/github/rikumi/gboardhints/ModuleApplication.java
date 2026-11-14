/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import android.app.Application;
import android.content.SharedPreferences;
import io.github.libxposed.service.*;
/** Publishes existing private display settings through the framework's remote preferences. */
public final class ModuleApplication extends Application {
    private XposedService service;
    private SharedPreferences local;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->publish();
    @Override public void onCreate() {
        super.onCreate();
        if(android.os.Build.VERSION.SDK_INT<26)return;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService bound) {
                service=bound;
                if(local==null) {
                    local=ConfigStore.prefs(ModuleApplication.this);
                    local.registerOnSharedPreferenceChangeListener(changes);
                }
                publish();
            }
            @Override public void onServiceDied(XposedService lost) { if(service==lost)service=null; }
        });
    }
    private synchronized void publish() {
        if(service==null)return;
        try {
            SharedPreferences remote=service.getRemotePreferences("config");
            copy(local,remote);
            StartupDiagnostics.record("config.remote.published");
        }catch(RuntimeException error){StartupDiagnostics.failure("config.remote.publish",error);}
    }
    static void copy(SharedPreferences local,SharedPreferences remote) {
            SharedPreferences.Editor edit=remote.edit().clear();
            for(java.util.Map.Entry<String,?> entry:local.getAll().entrySet()) {
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof String)edit.putString(key,(String)value);
            }
            if(!edit.putBoolean("_snapshot_ready",true).commit())throw new IllegalStateException("Remote preferences commit failed");
    }
}
