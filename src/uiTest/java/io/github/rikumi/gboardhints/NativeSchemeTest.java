/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeSchemeTest {
    public static class CachedPreferences {
        String value="shuangpin_flypy";
        public SharedPreferences backing(){return null;}
        public String effective(String key,String fallback){assertEquals("pinyin_scheme",key);return value;}
    }
    public static class BaseDecoder { private final CachedPreferences options=new CachedPreferences(); }
    public static class Decoder extends BaseDecoder { }
    @Test public void readsEffectiveCachedProfileWithoutAnySharedPreferencesCallback() throws Exception {
        Decoder decoder=new Decoder();assertEquals("shuangpin_flypy",NativeScheme.read(decoder));
        ((BaseDecoder)decoder).options.value="pinyin";assertEquals("pinyin",NativeScheme.read(decoder));
    }
    @Test public void unavailableBackendDoesNotInventADefaultScheme() throws Exception {
        assertNull(NativeScheme.read(new Object()));
    }
}
