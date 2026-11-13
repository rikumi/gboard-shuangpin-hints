/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GboardSettingsEntryTest {
    // These obfuscated shapes are audited against Gboard 9, 15 and 16 APKs.
    public static class NativePreference implements Comparable<NativePreference> {
        public final Context j;
        public String r, t, x;
        public Intent s;
        public CharSequence q;
        private CharSequence a;
        public int p = Integer.MAX_VALUE, layout = 0x7f030001;
        public NativePreference(Context ctx) { j = ctx; }
        public int compareTo(NativePreference other) {
            int byOrder = Integer.compare(p, other.p);
            return byOrder != 0 ? byOrder : String.valueOf(q).compareTo(String.valueOf(other.q));
        }
        void click() { j.startActivity(s); }
    }
    public static class NativeList extends NativePreference { public NativeList(Context ctx) { super(ctx); } }
    public static class NativeGroup extends NativePreference {
        public final List<NativePreference> b = new ArrayList<>();
        public NativeGroup(Context ctx) { super(ctx); }
        public void al(NativePreference child) { if (!b.contains(child)) { b.add(child); Collections.sort(b); } }
        public void am(NativePreference child) { b.remove(child); }
    }
    private GboardSettingsEntryAdapter adapter() throws Exception {
        return new GboardSettingsEntryAdapter(NativePreference.class, NativeGroup.class, NativeList.class);
    }
    private NativeList scheme(Context ctx) {
        NativeList scheme = new NativeList(ctx); scheme.r = "pinyin_scheme"; scheme.q = "双拼方案"; scheme.p = 5; return scheme;
    }
    @Test public void entryRemainsImmediatelyAfterSchemeAcrossSortingAndRepeatedAdds() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx);
        GboardSettingsEntryAdapter adapter = adapter(); NativeList scheme = scheme(ctx);
        NativePreference first = new NativePreference(ctx); first.p = 0; first.q = "first"; group.al(first);
        group.al(scheme);
        Method add = NativeGroup.class.getMethod("al", NativePreference.class);
        adapter.afterOperation(group, add, scheme);
        assertEquals(3, group.b.size()); assertEquals(GboardSettingsEntryAdapter.TITLE, group.b.get(2).q);
        NativePreference next = new NativePreference(ctx); next.p = 6; next.q = "next"; group.al(next);
        adapter.afterOperation(group, add, next);
        Collections.sort(group.b); adapter.reorder(group);
        assertEquals(scheme, group.b.get(1)); assertEquals(GboardSettingsEntryAdapter.KEY, group.b.get(2).r);
        assertEquals(next, group.b.get(3));
        adapter.afterOperation(group, add, scheme); assertEquals(4, group.b.size());
    }
    @Test public void directSettingsIntentWorksWhenModuleLauncherAliasIsHidden() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx); NativeList scheme = scheme(ctx);
        group.al(scheme); adapter().afterOperation(group, NativeGroup.class.getMethod("al", NativePreference.class), scheme);
        LauncherIcon.setHidden(ctx, true);
        try {
            group.b.get(1).click();
            Intent started = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(new ComponentName(ctx, SettingsActivity.class), started.getComponent());
            assertNotNull(ctx.getPackageManager().resolveActivity(started, 0));
        } finally { LauncherIcon.setHidden(ctx, false); }
    }
    @Test public void removingSchemeAlsoRemovesItsCompanionEntry() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx); NativeList scheme = scheme(ctx);
        GboardSettingsEntryAdapter adapter = adapter(); group.al(scheme);
        adapter.afterOperation(group, NativeGroup.class.getMethod("al", NativePreference.class), scheme);
        group.am(scheme);
        adapter.afterOperation(group, NativeGroup.class.getMethod("am", NativePreference.class), scheme);
        assertTrue(group.b.isEmpty());
    }
    @Test public void unrelatedListPreferencesDoNotGainAnEntry() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx); NativeList other = scheme(ctx);
        other.r = "keyboard_theme"; group.al(other);
        adapter().afterOperation(group, NativeGroup.class.getMethod("al", NativePreference.class), other);
        assertEquals(1, group.b.size());
    }
}
