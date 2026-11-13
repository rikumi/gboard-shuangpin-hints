/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import android.content.Context;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import com.google.android.libraries.inputmethod.metadata.*;
import com.google.android.libraries.inputmethod.metadata.ActionDef.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ModernNativeKeysTest {
    public static final class Key extends FrameLayout {
        public SoftKeyDef metadata;public long flags;
        public Key(Context c){super(c);}
        public boolean s(SoftKeyDef d,long flags){metadata=d;this.flags=flags;return true;}
    }
    private SoftKeyDef definition(){
        ActionDef a=new ActionDef(Phase.PRESS,Items.q(new Object[]{new Event(40,Intention.DECODE,"l",0)}),true,false,0,false,false,false,0,0,"l",Items.q(new Object[]{"l"}),new int[0],false);
        return new SoftKeyDef(new Object(),android.R.id.button1,android.R.layout.simple_list_item_1,Items.q(new Object[]{a}),Items.q(new Object[]{"l","3"}),new int[0],Items.q(new Object[]{1}),new int[0],"l","l");
    }
    @Test public void immutableMetadataCreatesNativeSemicolonWithCorrectActionAndLabel() throws Exception {
        Key key=new Key(RuntimeEnvironment.getApplication());key.s(definition(),7);
        NativeKeyAdapter adapter=new NativeKeyAdapter(Key.class,Key.class.getField("metadata"));
        assertEquals('l',AdaptiveKeyAdapter.key(key,adapter.metadata));SoftKeyDef original=key.metadata;
        SoftKeyDef decoded=(SoftKeyDef)adapter.key(key,android.R.id.button2,";",";",true);
        ActionDef binding=(ActionDef)decoded.i.get(0);
        assertEquals(";",decoded.j.get(0));assertEquals(android.R.id.button2,decoded.g);
        assertEquals(";",binding.event().data);assertEquals(Intention.DECODE,binding.event().intention);
        assertEquals("l",((ActionDef)original.i.get(0)).event().data);assertEquals("l",original.j.get(0));
        adapter.bind(key,decoded);assertEquals(7,key.flags);assertEquals(";",adapter.pressText(key.metadata));
        SoftKeyDef committed=(SoftKeyDef)adapter.key(key,android.R.id.button2,";","；",false);
        assertEquals(Intention.COMMIT,((ActionDef)committed.i.get(0)).event().intention);
        assertEquals("；",((ActionDef)committed.i.get(0)).event().data);
    }
    public static final class Input {
        public Phase phase=Phase.PRESS;public Event[] events;
        Input(Event...e){events=e;}
    }
    public interface Dispatch { boolean dispatch(Input input); }
    public abstract static class Base implements Dispatch { }
    public static final class Ime extends Base { @Override public boolean dispatch(Input input){return true;} }
    @Test public void fourArgumentNativeEventsSupportOneShotUppercaseWithoutCommit() throws Exception {
        InlineUppercaseAdapter adapter=InlineUppercaseAdapter.discover(Ime.class);assertNotNull(adapter);
        Input input=new Input(new Event(29,Intention.DECODE,"a",0));
        assertTrue(adapter.press(input));assertTrue(adapter.uppercase(input));
        assertEquals("A",input.events[0].data);assertEquals(Intention.DECODE,input.events[0].intention);
        assertTrue(adapter.uppercaseDecode(input.events[0]));
        assertTrue(adapter.separator(new Input(new Event(75,Intention.DECODE,"'",0))));
    }
    @Test public void separatorDispatchArmsExactlyOneChineseLetterAndLeavesEnglishAndPasswordsNative() throws Exception {
        HintController controller=new HintController(); controller.inlineSupported(true);
        org.robolectric.util.ReflectionHelpers.setField(controller,"service",new android.inputmethodservice.InputMethodService());
        org.robolectric.util.ReflectionHelpers.setField(controller,"visible",true);
        org.robolectric.util.ReflectionHelpers.setField(controller,"inlineUppercaseEnabled",true);
        InlineUppercaseAdapter adapter=InlineUppercaseAdapter.discover(Ime.class);
        InlineUppercaseDispatcher dispatcher=new InlineUppercaseDispatcher(adapter,controller);
        Input separator=new Input(new Event(75,Intention.DECODE,"'",0));
        assertTrue(dispatcher.handle(separator,true));assertTrue(controller.inlineUppercaseArmed());
        Input letter=new Input(new Event(29,Intention.DECODE,"a",0));
        assertFalse(dispatcher.handle(letter,true));assertEquals("A",letter.events[0].data);
        assertFalse(controller.inlineUppercaseArmed());assertTrue(dispatcher.acceptsUppercase(letter.events[0],true));
        Input next=new Input(new Event(30,Intention.DECODE,"b",0));dispatcher.handle(next,true);
        assertEquals("b",next.events[0].data);
        assertFalse(dispatcher.handle(separator,false));assertFalse(dispatcher.acceptsUppercase(letter.events[0],false));
        org.robolectric.util.ReflectionHelpers.setField(controller,"password",true);
        assertFalse(dispatcher.handle(separator,true));assertFalse(dispatcher.acceptsUppercase(letter.events[0],true));
    }

}
