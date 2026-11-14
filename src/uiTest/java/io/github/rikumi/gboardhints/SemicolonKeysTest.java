/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.content.*;
import android.content.res.Resources;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import com.google.android.libraries.inputmethod.widgets.SoftKeyboardView;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = SemicolonKeysTest.Logging.class)
public class SemicolonKeysTest {
    @org.robolectric.annotation.Implements(value=de.robv.android.xposed.XposedBridge.class,isInAndroidSdk=false)
    public static class Logging {
        @org.robolectric.annotation.Implementation public static void log(String message) { }
    }
    public enum Intention { DECODE, COMMIT }
    public enum Phase { PRESS, LONG_PRESS }
    public static final class Action {
        public final int code; public final Intention intention; public final Object text;
        private int cachedHash;
        public Action(int code, Intention intention, Object text) { this.code = code; this.intention = intention; this.text = text; }
    }
    public static final class BindingBuilder { public BindingBuilder() { } }
    public static final class Binding {
        public final Phase phase; public final Action[] actions; public final String[] descriptions;
        public Binding(BindingBuilder builder) { phase = Phase.PRESS; actions = new Action[]{new Action(40,Intention.DECODE,"l")}; descriptions = new String[]{"l"}; }
    }
    public static final class DefinitionBuilder { public DefinitionBuilder() { } }
    public static final class Definition {
        public final int id; public final int layout;
        public final CharSequence[] labels; public final Binding[] bindings; public final String description;
        public Definition(DefinitionBuilder builder) { id = android.R.id.button1; layout = android.R.layout.simple_list_item_1;
            labels = new CharSequence[]{"l",""}; bindings = new Binding[]{new Binding(new BindingBuilder())}; description = "l"; }
    }
    public static class Key extends FrameLayout {
        public Definition definition; public long state;
        public TextView label;
        public final int nativeStyle = 0;
        public int sourceLayout;
        public ViewGroup contentHost;
        @Override public int getSourceLayoutResId(){return sourceLayout;}
        public Key(Context context) {this(context,null);}
        public Key(Context context,android.util.AttributeSet attrs) { super(context,attrs); }
        @Override public void onFinishInflate(){super.onFinishInflate();contentHost=findViewById(android.R.id.content);}
        public boolean bind(Definition value, long flags) {
            state=flags;
            if(value==definition)return false;
            definition=value;
            if(value==null) {
                if(contentHost!=null)contentHost.removeAllViews();else removeAllViews();
                label=null;setVisibility(View.INVISIBLE);return true;
            }
            if(label==null || label.getParent()==null) {
                label=new TextView(getContext());label.setTextSize(24);
                (contentHost==null?this:contentHost).addView(label,new FrameLayout.LayoutParams(-1,-1));
            }
            label.setText(value.labels[0]);setVisibility(View.VISIBLE);return true;
        }
    }
    private org.robolectric.android.controller.ActivityController<Activity> activity;
    private SoftKeyboardView keyboard;
    private LinearLayout top, middle;
    private final List<View> observed = new ArrayList<>();
    private final Map<String,Integer> ids = new HashMap<>();
    private NativeKeyAdapter adapter;
    private SemicolonKeys feature;
    private Key shift, l;
    private Context context;
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        Resources original = activity.get().getResources();
        Resources resources = new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()) {
            @Override public String getResourceEntryName(int id) {
                for (Map.Entry<String,Integer> item : ids.entrySet()) if (item.getValue() == id) return item.getKey();
                return original.getResourceEntryName(id);
            }
            @Override public String getResourceTypeName(int id) {
                return ids.containsValue(id) ? "id" : original.getResourceTypeName(id);
            }
            @Override public int getIdentifier(String name, String type, String pkg) {
                Integer id = ids.get(name); return id == null ? 0 : id;
            }
        };
        context = new ContextWrapper(activity.get()) { @Override public Resources getResources() { return resources; } };
        ids.put("C10",0x10000100);
        keyboard = new SoftKeyboardView(context);
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); keyboard.addView(body,new FrameLayout.LayoutParams(1000,300));
        top = new LinearLayout(context); middle = new LinearLayout(context);
        top.setWeightSum(1000); middle.setWeightSum(1000);
        body.addView(top,new LinearLayout.LayoutParams(1000,100)); body.addView(middle,new LinearLayout.LayoutParams(1000,100));
        for (int i = 0; i < 10; i++) add(top,"D"+String.format(java.util.Locale.ROOT,"%02d",i+1),"qwertyuiop".charAt(i));
        middle.addView(new Space(context),new LinearLayout.LayoutParams(0,100,50));
        for (int i = 0; i < 9; i++) l = add(middle,"C"+String.format(java.util.Locale.ROOT,"%02d",i+1),"asdfghjkl".charAt(i));
        middle.addView(new Space(context),new LinearLayout.LayoutParams(0,100,50));
        LinearLayout bottom = new LinearLayout(context); body.addView(bottom,new LinearLayout.LayoutParams(1000,100));
        shift = add(bottom,"key_pos_shift",';');
        activity.get().setContentView(keyboard);
        adapter = new NativeKeyAdapter(Key.class,Key.class.getField("definition")); feature = new SemicolonKeys(adapter);
        layout();
    }
    private Key add(LinearLayout row,String name,char letter) throws Exception {
        ids.put(name,0x10000000+ids.size()); Key key = new Key(context); key.setId(ids.get(name));
        Definition definition = new Definition(new DefinitionBuilder());
        java.lang.reflect.Field labels = Definition.class.getField("labels"); labels.setAccessible(true); labels.set(definition,new CharSequence[]{String.valueOf(letter),""});
        key.bind(definition,7L); row.addView(key,new LinearLayout.LayoutParams(0,100,100)); observed.add(key); return key;
    }
    private void layout() {
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));
        keyboard.layout(0,0,1000,300);
    }
    private Key semicolon() { return (Key)keyboard.findViewById(ids.get("C10")); }
    private Action action(Key key) { return key.definition.bindings[0].actions[0]; }
    @After public void cleanup() { if (feature != null) feature.restore(); activity.pause().stop().destroy(); }
    @Test public void tenthNativeKeyAlignsWithEveryTopKeyAndDisablingRestoresStagger() throws Exception {
        int originalLeft = middle.getChildAt(1).getLeft(); Definition source = l.definition;
        feature.update(observed,true,true,true,false,"MS"); layout();
        assertNotNull(semicolon()); assertSame(semicolon(),keyboard.registered.get(ids.get("C10")));
        List<View> row = new ArrayList<>(); for (View view : observed) if (view.getParent()==middle) row.add(view); row.add(semicolon());
        for (int i=0;i<10;i++) { assertEquals(top.getChildAt(i).getLeft(),row.get(i).getLeft()); assertEquals(top.getChildAt(i).getWidth(),row.get(i).getWidth()); }
        assertEquals(";",semicolon().label.getText().toString()); assertEquals(7L,semicolon().state);
        assertEquals(Intention.COMMIT,action(semicolon()).intention); assertEquals("；",action(semicolon()).text);
        assertSame(source.labels,l.definition.labels); assertSame(source.bindings,l.definition.bindings); assertEquals("l",l.label.getText().toString());
        feature.update(observed,false,true,true,false,"MS"); layout();
        assertEquals(View.GONE,semicolon().getVisibility()); assertEquals(originalLeft,middle.getChildAt(1).getLeft());
        feature.update(observed,true,true,true,false,"MS"); layout();
        assertEquals(1,keyboard.registered.size());
    }
    @Test @Config(sdk=31) public void clonedKeyIncludesNativeXmlContentHostAndStyle() throws Exception {
        View root=android.view.LayoutInflater.from(activity.get()).inflate(R.layout.test_native_key_source,null,false);
        Key original=root.findViewById(android.R.id.button1);original.sourceLayout=R.layout.test_native_key_source;
        Key clone=(Key)adapter.createLike(original);
        assertNotSame(original,clone);assertNull(clone.getParent());
        assertNotNull(clone.findViewById(android.R.id.content));
        assertNotSame(original.findViewById(android.R.id.content),clone.findViewById(android.R.id.content));
        assertEquals(original.getPaddingLeft(),clone.getPaddingLeft());
        assertNotNull(root.findViewById(android.R.id.button1));
    }
    @Test public void edgeKeyPaddingAndWidthsFollowTopColumnsAndRestoreAfterDisable() throws Exception {
        View a=middle.getChildAt(1);a.setPadding(40,3,0,4);l.setPadding(0,3,40,4);
        for(int i=0;i<10;i++)top.getChildAt(i).setPadding(3,5,3,6);
        java.lang.reflect.Field style=Key.class.getField("nativeStyle");style.setAccessible(true);style.setInt(top.getChildAt(9),17);
        feature.update(observed,true,true,true,false,"MS");layout();
        assertEquals(17,style.getInt(semicolon()));
        for(int i=0;i<10;i++) {
            View target=i==9?semicolon():i==0?a:middle.findViewById(ids.get("C"+String.format(java.util.Locale.ROOT,"%02d",i+1)));
            assertEquals(top.getChildAt(i).getWidth(),target.getWidth());
            assertEquals(3,target.getPaddingLeft());assertEquals(3,target.getPaddingRight());
            assertEquals(top.getChildAt(i).getWidth()-6,target.getWidth()-target.getPaddingLeft()-target.getPaddingRight());
        }
        a.setPadding(40,3,0,4);l.setPadding(0,3,40,4);
        feature.update(observed,true,true,true,false,"MS");layout();
        assertEquals(3,a.getPaddingLeft());assertEquals(3,l.getPaddingRight());
        feature.update(observed,false,true,true,false,"MS");assertEquals(40,a.getPaddingLeft());assertEquals(40,l.getPaddingRight());
    }
    @Test public void weightedEdgeContentHostsMatchOrdinaryKeysAndRestoreNativeWeights() throws Exception {
        Key a=(Key)middle.getChildAt(1);
        LinearLayout aWrapper=weightedHost(a,60,56),lWrapper=weightedHost(l,69,65);
        layout();
        assertTrue(a.contentHost.getWidth()<a.getWidth());assertTrue(l.contentHost.getWidth()<l.getWidth());
        assertTrue(a.contentHost.getHeight()<a.getHeight());assertTrue(l.contentHost.getHeight()<l.getHeight());
        feature.update(observed,true,true,true,false,"MS");layout();
        assertEquals(a.getWidth(),a.label.getWidth());assertEquals(l.getWidth(),l.label.getWidth());
        assertEquals(a.getHeight(),a.label.getHeight());assertEquals(l.getHeight(),l.label.getHeight());
        assertNull(a.contentHost);assertNull(l.contentHost);
        feature.update(observed,false,true,true,false,"MS");layout();
        assertEquals(60f,aWrapper.getWeightSum(),0f);assertEquals(69f,lWrapper.getWeightSum(),0f);
        assertTrue(a.contentHost.getWidth()<a.getWidth());assertTrue(l.contentHost.getWidth()<l.getWidth());
        assertTrue(a.contentHost.getHeight()<a.getHeight());assertTrue(l.contentHost.getHeight()<l.getHeight());
    }
    private LinearLayout weightedHost(Key key,int sum,int weight) {
        key.removeView(key.label);
        LinearLayout wrapper=new LinearLayout(context);wrapper.setWeightSum(sum);
        FrameLayout host=new FrameLayout(context);host.addView(key.label);key.contentHost=host;
        wrapper.addView(host,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,weight));
        key.addView(wrapper,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,80));
        return wrapper;
    }
    @Test public void invalidSemicolonRowDoesNotPreventShiftFromBecomingUppercase() throws Exception {
        Key extra=add(top,"D11",'x');
        feature.update(observed,true,true,true,true,"MS",true);
        assertEquals("⇧",shift.label.getText().toString());assertNull(semicolon());
    }
    @Test public void nativeShiftWithoutPressBindingStillKeepsItsPresentationAndGetsASeparatorAction() throws Exception {
        java.lang.reflect.Field labels=Definition.class.getField("labels");labels.setAccessible(true);labels.set(shift.definition,new CharSequence[]{"",""});
        java.lang.reflect.Field phase=Binding.class.getField("phase");phase.setAccessible(true);phase.set(shift.definition.bindings[0],Phase.LONG_PRESS);
        feature.update(observed,false,true,true,true,"MS",true);
        assertEquals("",shift.label.getText().toString());assertEquals("'",action(shift).text);
        assertEquals(Phase.PRESS,shift.definition.bindings[0].phase);
    }
    @Test public void uppercaseReusesNativeShiftPresentationInsteadOfDrawingALetterGlyph() throws Exception {
        java.lang.reflect.Field labels=Definition.class.getField("labels");labels.setAccessible(true);labels.set(shift.definition,new CharSequence[]{"",""});
        java.lang.reflect.Field actions=Binding.class.getField("actions");actions.setAccessible(true);
        actions.set(shift.definition.bindings[0],new Action[]{new Action(-100,Intention.DECODE,null)});
        Object source=shift.definition;
        feature.update(observed,false,true,true,true,"MS",true);
        assertEquals("",shift.label.getText().toString());assertEquals("'",action(shift).text);
        feature.update(observed,false,false,false,false,"MS",false);assertSame(source,shift.definition);
    }
    @Test public void semicolonUsesNativeDecodeOnlyWithPreeditAndSchemeBinding() throws Exception {
        for(String scheme:new String[]{"MS","Ziguang","XIAOHE","Ziranma","ABC","PinyinJiaJia"}) for(boolean composing:new boolean[]{false,true}) {
            feature.update(observed,true,true,true,composing,scheme);
            boolean decode = composing && (scheme.equals("MS") || scheme.equals("Ziguang"));
            assertEquals(decode ? Intention.DECODE : Intention.COMMIT,action(semicolon()).intention);
            assertEquals(decode ? ";" : "；",action(semicolon()).text); assertEquals(74,action(semicolon()).code);
        }
    }
    @Test public void inlineUppercaseOnlyReplacesShiftWhilePreeditExists() throws Exception {
        Definition original=shift.definition;
        for(boolean composing:new boolean[]{false,true,false}) {
            feature.update(observed,false,true,true,composing,"MS",true);
            if(composing) {
                assertEquals("⇧",shift.label.getText().toString());assertEquals("'",action(shift).text);
                assertEquals(Intention.DECODE,action(shift).intention);
            } else assertSame(original,shift.definition);
        }
        feature.update(observed,false,true,true,true,"MS",false);
        assertEquals("'",shift.label.getText().toString());
        feature.update(observed,false,false,false,false,"MS",false);assertSame(original,shift.definition);
    }
    @Test public void shiftAlwaysUsesApostropheWhileComposingAndRestoresNativeDefinition() throws Exception {
        Definition original = shift.definition;
        feature.update(observed,false,true,true,true,"MS");
        assertNull(semicolon()); assertEquals("'",shift.label.getText().toString());
        assertEquals("'",action(shift).text); assertEquals(Intention.DECODE,action(shift).intention); assertEquals(75,action(shift).code);
        feature.update(observed,false,true,true,false,"MS"); assertSame(original,shift.definition);
        feature.update(observed,true,false,false,true,"MS"); assertNull(semicolon()); assertSame(original,shift.definition);
    }
    @Test @Config(sdk=23) public void englishAndNonPinyinChineseNeverSendShuangpinFinals() throws Exception {
        feature.update(observed,true,false,false,false,"MS"); assertNull(semicolon());
        feature.update(observed,true,true,false,true,"MS");
        assertEquals("；",action(semicolon()).text); assertEquals(Intention.COMMIT,action(semicolon()).intention);
        feature.update(observed,true,false,false,false,"MS"); assertEquals(View.GONE,semicolon().getVisibility());
    }
    @Test public void cachedKeyboardCanReuseItsInjectedKeyAfterRestoreAndRebinding() throws Exception {
        feature.update(observed,true,true,true,true,"MS"); Key first=semicolon();
        feature.restore(); assertEquals(View.GONE,first.getVisibility());
        feature.update(observed,true,true,true,false,"XIAOHE");
        assertSame(first,semicolon()); assertEquals(1,keyboard.registered.size());
        assertEquals("；",action(first).text);
        first.bind(null,0); feature.update(observed,true,true,true,true,"Ziguang");
        assertEquals(View.VISIBLE,first.getVisibility()); assertEquals(";",action(first).text);
    }

    public static final class LegacyKey extends FrameLayout {
        public Definition definition;
        public LegacyKey(Context context) { super(context); }
        public void bind(Definition definition) { this.definition=definition; }
    }
    @Test public void legacyOneArgumentBinderCopiesImmutableDefinitionsWithoutEditingNativeSource() throws Exception {
        NativeKeyAdapter legacy = new NativeKeyAdapter(LegacyKey.class,LegacyKey.class.getField("definition"));
        LegacyKey source = new LegacyKey(context); Definition original = new Definition(new DefinitionBuilder()); source.bind(original);
        LegacyKey key = (LegacyKey)legacy.create(context);
        Definition copy = (Definition)legacy.key(source,ids.get("C10"),";",";",true); legacy.bind(key,copy);
        assertEquals(";",key.definition.labels[0]); assertEquals(Intention.DECODE,key.definition.bindings[0].actions[0].intention);
        assertEquals("l",original.labels[0]); assertEquals("l",original.bindings[0].actions[0].text);
        assertNotSame(original.bindings[0],copy.bindings[0]);
    }

    @Test public void nativeEmptyTenthSlotIsReusedAndItsOriginalStateIsRestored() throws Exception {
        Key placeholder = new Key(context); placeholder.setId(ids.get("C10")); placeholder.bind(null,0); placeholder.setVisibility(View.GONE);
        middle.addView(placeholder,new LinearLayout.LayoutParams(0,100,100));
        feature.update(observed,true,true,true,true,"MS");
        assertSame(placeholder,semicolon()); assertEquals(";",action(placeholder).text);
        feature.update(observed,false,true,true,false,"MS");
        assertNull(placeholder.definition); assertEquals(View.GONE,placeholder.getVisibility());
        feature.update(observed,true,true,true,false,"MS"); assertEquals("；",action(placeholder).text);
    }

}
