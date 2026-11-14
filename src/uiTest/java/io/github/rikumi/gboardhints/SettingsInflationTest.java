/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import android.content.Context;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsInflationTest {
    static class Group { }
    static class Screen extends Group { }
    static class Manager {
        Screen inflate(Context context,int xml,Screen parent){return parent;}
        Group unrelated(int value){return null;}
    }
    static class ParentFragment { Manager manager; }
    static class Fragment extends ParentFragment { }
    @Test public void findsInheritedManagerScreenInflationBeforeAdapterConstruction() throws Exception {
        Set<Method> found=SettingsInflation.find(Fragment.class,Group.class);
        assertEquals(1,found.size());assertEquals(Manager.class.getDeclaredMethod("inflate",Context.class,int.class,Screen.class),found.iterator().next());
    }
}
