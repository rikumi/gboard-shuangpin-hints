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
        private int nextOrder;
        public NativeGroup(Context ctx) { super(ctx); }
        public void al(NativePreference child) {
            if (!b.contains(child)) {
                if (child.p == Integer.MAX_VALUE) child.p = nextOrder++;
                b.add(child); Collections.sort(b);
            }
        }
        public void am(NativePreference child) { b.remove(child); }
    }
    private GboardSettingsEntryAdapter adapter() throws Exception {
        return new GboardSettingsEntryAdapter(NativePreference.class, NativeGroup.class, NativeList.class);
    }
    private NativeList scheme(Context ctx) {
        NativeList scheme = new NativeList(ctx); scheme.r = "pinyin_scheme"; scheme.q = "双拼方案"; return scheme;
    }
    @Test public void entryRemainsImmediatelyAfterSchemeAcrossSortingAndRepeatedAdds() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx);
        GboardSettingsEntryAdapter adapter = adapter(); NativeList scheme = scheme(ctx);
        NativePreference first = new NativePreference(ctx); first.q = "first"; group.al(first);
        group.al(scheme);
        Method add = NativeGroup.class.getMethod("al", NativePreference.class);
        adapter.afterOperation(group, add, scheme);
        assertEquals(3, group.b.size()); assertEquals(GboardSettingsEntryAdapter.TITLE, group.b.get(2).q);
        NativePreference next = new NativePreference(ctx); next.q = "next"; group.al(next);
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
    @Test public void nativeAdapterCanSortItsOwnCopyWithoutMovingEntryAboveScheme() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication(); NativeGroup group = new NativeGroup(ctx);
        NativeList scheme = scheme(ctx); group.al(scheme);
        GboardSettingsEntryAdapter adapter = adapter();
        Method add = NativeGroup.class.getMethod("al", NativePreference.class);
        adapter.afterOperation(group, add, scheme);
        NativePreference following = new NativePreference(ctx); following.q = "中英文"; group.al(following);
        List<NativePreference> adapterRows = new ArrayList<>(group.b);
        Collections.sort(adapterRows);
        assertEquals(scheme, adapterRows.get(0));
        assertEquals(GboardSettingsEntryAdapter.KEY, adapterRows.get(1).r);
        assertEquals(following, adapterRows.get(2));
        assertTrue(adapterRows.get(1).p > scheme.p);
        assertTrue(following.p > adapterRows.get(1).p);
    }

    @Test public void inlinedInflationAndLateKeyAssignmentStillCreateOneNativeEntry() throws Exception {
        Context ctx=RuntimeEnvironment.getApplication();NativeGroup group=new NativeGroup(ctx);
        GboardSettingsEntryAdapter adapter=adapter();adapter.observe(group);
        NativeList scheme=new NativeList(ctx);group.al(scheme);
        adapter.reconcileAll();assertEquals(1,group.b.size());
        NativePreference next=new NativePreference(ctx);next.q="next";group.al(next);
        scheme.r="pinyin_scheme";scheme.q="双拼方案";
        adapter.reconcileAll();adapter.reconcileAll();
        Collections.sort(group.b);
        assertEquals(3,group.b.size());assertSame(scheme,group.b.get(0));
        assertEquals(GboardSettingsEntryAdapter.KEY,group.b.get(1).r);
        assertEquals(new ComponentName(ctx,SettingsActivity.class),group.b.get(1).s.getComponent());
    }

    @Test public void adapterCreationObservesAnAlreadyInflatedScreenAndItsCategories() throws Exception {
        Context ctx=RuntimeEnvironment.getApplication();NativeGroup screen=new NativeGroup(ctx),category=new NativeGroup(ctx);
        screen.al(category);NativeList scheme=scheme(ctx);category.al(scheme);
        GboardSettingsEntryAdapter adapter=adapter();adapter.observeTree(screen);adapter.reconcileAll();
        assertEquals(2,category.b.size());assertEquals(GboardSettingsEntryAdapter.KEY,category.b.get(1).r);
    }

    @Test public void staleScreenCannotPreventEntryInCurrentScreen() throws Exception {
        Context ctx=RuntimeEnvironment.getApplication();
        NativeGroup stale=new NativeGroup(ctx){@Override public void al(NativePreference preference){throw new IllegalStateException("detached manager");}};
        stale.b.add(scheme(ctx));NativeGroup current=new NativeGroup(ctx);current.al(scheme(ctx));
        GboardSettingsEntryAdapter adapter=adapter();adapter.observeTree(stale);adapter.observeTree(current);
        try {adapter.reconcileAll();}catch(java.lang.reflect.InvocationTargetException expected) { }
        assertEquals(2,current.b.size());assertEquals(GboardSettingsEntryAdapter.KEY,current.b.get(1).r);
    }

}
