/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import android.net.Uri;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ConfigAccessTest {
    @Test public void accessRequestCannotRedirectGrantOrObtainWriteAccess() {
        List<Uri> uris = new ArrayList<>();
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public void grantUriPermission(String target, Uri uri,int flags) {
                assertEquals("com.google.android.inputmethod.latin",target);
                assertEquals(0,flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                assertTrue((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0);
                assertTrue((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
                if(uri.equals(ConfigAccessReceiver.EMOJI))
                    assertTrue((flags & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)!=0);
                else assertEquals(ConfigProvider.URI,uri);
                uris.add(uri);
            }
        };
        ConfigAccessReceiver receiver = new ConfigAccessReceiver();
        receiver.onReceive(context,new Intent("unrelated"));
        assertTrue(uris.isEmpty());
        receiver.onReceive(context,new Intent(ConfigAccessReceiver.ACTION)
                .putExtra("package","other.app").setData(Uri.parse("content://other/private")));
        assertEquals(Arrays.asList(ConfigProvider.URI,ConfigAccessReceiver.EMOJI),uris);
        uris.clear();
        receiver.onReceive(context,new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED));
        assertEquals(2,uris.size());
    }
    @Test public void emojiLoadsDuringApplicationAttachWithoutApplicationContext() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        ConfigProvider provider = Robolectric.buildContentProvider(ConfigProvider.class).create().get();
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(EmojiLexicon.AUTHORITY,provider);
        Context early = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return null; }
        };
        Map<String,EmojiLexicon> cache = org.robolectric.util.ReflectionHelpers.getStaticField(EmojiLexicon.class,"cache");
        cache.clear();
        EmojiLexicon.request(early,"Ziranma",null);
        java.util.concurrent.ExecutorService loader = org.robolectric.util.ReflectionHelpers.getStaticField(EmojiLexicon.class,"loader");
        loader.submit(() -> {}).get(10,java.util.concurrent.TimeUnit.SECONDS);
        EmojiLexicon dictionary = EmojiLexicon.request(early,"Ziranma",null);
        assertNotNull(dictionary);
        assertEquals("💩",dictionary.match("ui").get(0));
        cache.clear();
    }
}
