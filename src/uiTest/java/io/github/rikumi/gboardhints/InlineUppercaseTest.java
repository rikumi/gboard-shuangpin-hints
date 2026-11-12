/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.app.Activity;
import android.content.*;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class InlineUppercaseTest {
    public enum Intent { DECODE, COMMIT }
    public enum Phase { PRESS, DOWN, UP, LONG_PRESS }
    public enum Other { UNKNOWN }
    public static final class Action {
        public final int code; public final Intent intention; public final Object text;
        public Action(int code, Intent intention, Object text) { this.code = code; this.intention = intention; this.text = text; }
    }
    public static final class Binding {
        public Phase phase = Phase.PRESS; public Action[] actions = {new Action(75, Intent.DECODE, "'")};
    }
    public static final class Definition {
        public int id = 123; public CharSequence[] labels = {"'"}; public Binding[] bindings = {new Binding()};
    }
    public static final class Event {
        public Action[] actions; public Phase phase = Phase.PRESS; public Definition metadata = new Definition(); public Other other = Other.UNKNOWN;
        Event(Action... actions) { this.actions = actions; }
    }
    public interface Dispatch { boolean send(Event e); }
    public abstract static class Base implements Dispatch { }
    public static class BaseDecodeProcessor { }
    public static final class ChineseDecoder extends BaseDecodeProcessor {
        protected boolean process(Event event) { return true; }
        public boolean admit(Action action) { return false; }
    }
    public static final class Manager { public BaseDecodeProcessor decoder = new ChineseDecoder(); }
    public static final class ErasedManager {
        public Object processors = new Object[]{new ChineseDecoder()};
        public Object primary = new ChineseDecoder();
    }
    public static final class ErasedHolder { public ErasedManager manager = new ErasedManager(); }
    public static final class Processor extends Base {
        public Manager manager = new Manager();
        public boolean admission(Event e) { return true; }
        @Override public boolean send(Event e) { return true; }
    }
    @Test public void dispatchDiscoveryUsesDecodeApostropheRegardlessOfObfuscatedId() throws Exception {
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        assertNotNull(adapter); assertEquals("send", adapter.dispatch.getName());
        Event event = new Event(new Action(75, Intent.DECODE, "'"));
        assertTrue(adapter.press(event)); assertTrue(adapter.separator(event));
        event.phase = Phase.DOWN; assertFalse(adapter.press(event));
        event.phase = Phase.UP; assertFalse(adapter.press(event));
        event.metadata.id = 456; assertTrue(adapter.separator(event));
        event.actions[0] = new Action(75, Intent.COMMIT, "'"); assertFalse(adapter.separator(event));
    }
    @Test public void symbolPageSwitcherAndLongPressRemainNativeEvenInShiftPosition() {
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        Definition definition = new Definition();
        definition.id = 456;
        assertTrue(adapter.replaceableDefinition(definition));
        definition.bindings[0].actions = new Action[]{new Action(-10004, null, "symbols")};
        assertFalse(adapter.replaceableDefinition(definition));
        definition.bindings[0].actions = new Action[]{new Action(75, Intent.DECODE, "'")};
        definition.bindings[0].phase = Phase.LONG_PRESS;
        assertFalse(adapter.replaceableDefinition(definition));
    }
    @Test public void uppercaseReplacesOnlyNextLetterWithDecodeAndDoesNotCommit() throws Exception {
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        Action original = new Action(29, Intent.DECODE, "a");
        Event event = new Event(original, new Action(47, Intent.DECODE, "s"));
        assertTrue(adapter.uppercase(event));
        assertEquals("A", event.actions[0].text); assertEquals("S", event.actions[1].text);
        assertEquals(Intent.DECODE, event.actions[0].intention); assertEquals("a", original.text);
        assertTrue(adapter.uppercaseDecode(event.actions[0]));
        Event number = new Event(new Action(8, Intent.COMMIT, "1"));
        assertFalse(adapter.uppercase(number)); assertEquals(Intent.COMMIT, number.actions[0].intention);
    }
    @Test public void toggleIsOneShotAndOnlyActiveForChineseOutsidePasswordFields() {
        HintController controller = new HintController(); controller.inlineSupported(true);
        ReflectionHelpers.setField(controller, "service", new android.inputmethodservice.InputMethodService());
        ReflectionHelpers.setField(controller, "inlineUppercaseEnabled", true);
        controller.decoderStarted(false, true);
        assertTrue(controller.inlineUppercaseActive());
        controller.toggleInlineUppercase(); assertTrue(controller.inlineUppercaseArmed());
        controller.cancelInlineUppercase(); assertFalse(controller.inlineUppercaseArmed());
        controller.toggleInlineUppercase(); controller.toggleInlineUppercase(); assertFalse(controller.inlineUppercaseArmed());
        controller.toggleInlineUppercase(); controller.finish(); assertFalse(controller.inlineUppercaseArmed());
        ReflectionHelpers.setField(controller, "password", true); assertFalse(controller.inlineUppercaseActive());
        ReflectionHelpers.setField(controller, "password", false);
        controller.decoderStarted(false, false); assertFalse(controller.inlineUppercaseActive());
    }
    @Test public void keyPresentationRestoresNativeLabelsAndDescriptionOnDisableOrReuse() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout key = new FrameLayout(activity); TextView label = new TextView(activity); label.setText("'");
        label.setAlpha(.7f); key.addView(label);
        ImageView image = new ImageView(activity); image.setImageDrawable(new ColorDrawable(Color.WHITE));
        image.setAlpha(.8f); key.addView(image); key.setContentDescription("分词符");
        activity.setContentView(key); key.layout(0, 0, 60, 80); label.layout(0, 0, 60, 80);
        InlineShiftKey shift = new InlineShiftKey(); Object def = new Object();
        shift.apply(key, def, true, false); assertEquals(0f, label.getAlpha(), 0f);
        assertEquals("行内大写", key.getContentDescription()); assertEquals(0f, image.getAlpha(), 0f);
        shift.apply(key, def, true, true); assertTrue(key.getContentDescription().toString().contains("下一字母"));
        shift.restore(); assertEquals(.7f, label.getAlpha(), 0f); assertEquals("分词符", key.getContentDescription());
        assertEquals("'", label.getText().toString()); assertEquals(.8f, image.getAlpha(), 0f);
    }
    public static final class NativeSeparatorKey extends FrameLayout {
        public Definition definition = new Definition();
        NativeSeparatorKey(Context context) { super(context); definition.id = 456; definition.labels = new CharSequence[0]; }
    }
    @Test public void controllerReplacesRealisticIconKeyWithObfuscatedDefinitionId() throws Exception {
        org.robolectric.android.controller.ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible();
        HintController controller = new HintController();
        try {
            NativeSeparatorKey key = new NativeSeparatorKey(activity.get()); ImageView icon = new ImageView(activity.get());
            icon.setImageDrawable(new ColorDrawable(Color.WHITE)); key.addView(icon);
            key.setContentDescription("分词符"); activity.get().setContentView(key);
            key.layout(0, 0, 60, 80); icon.layout(10, 10, 50, 60);
            controller.observe(key, AdaptiveKeyAdapter.metadataField(NativeSeparatorKey.class));
            controller.inlineSupported(true); controller.inlineAdapter(InlineUppercaseAdapter.discover(Processor.class));
            controller.start(new android.view.inputmethod.EditorInfo()); controller.decoderStarted(true, true);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ReflectionHelpers.setField(controller, "service", new android.inputmethodservice.InputMethodService());
            ReflectionHelpers.setField(controller, "inlineUppercaseEnabled", true);
            controller.refreshLater(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("行内大写", key.getContentDescription()); assertEquals(0f, icon.getAlpha(), 0f);
            ReflectionHelpers.setField(controller, "inlineUppercaseEnabled", false);
            controller.refreshLater(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1f, icon.getAlpha(), 0f); assertEquals("分词符", key.getContentDescription());
        } finally {
            ReflectionHelpers.setField(controller, "service", null); controller.detach(); activity.pause().stop().destroy();
        }
    }

    private HintController configuredController() {
        HintController controller = new HintController(); controller.inlineSupported(true);
        ReflectionHelpers.setField(controller, "service", new android.inputmethodservice.InputMethodService());
        ReflectionHelpers.setField(controller, "inlineUppercaseEnabled", true);
        return controller;
    }
    @Test public void primaryDecoderAndProtectedInputEntryAreRecognizedIndependentlyOfLifecycleFlag() throws Exception {
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        Processor pipeline = new Processor();
        assertSame(pipeline.manager.decoder, adapter.currentDecoder(pipeline));
        ErasedHolder erased = new ErasedHolder();
        assertSame("Erased Object fields must work and direct primary beats processor-array fallback",
                erased.manager.primary, adapter.currentDecoder(erased));
        assertTrue(adapter.decoderEventMethod(ChineseDecoder.class.getDeclaredMethod("process", Event.class)));
        assertFalse(adapter.decoderEventMethod(ChineseDecoder.class.getDeclaredMethod("admit", Action.class)));
        HintController controller = configuredController();
        controller.inlineDecoderObserved(true); controller.decoderStarted(false, false);
        assertTrue("An auxiliary HMM callback cannot disable the primary Chinese decoder", controller.inlineUppercaseActive());
        controller.inlineDecoderObserved(false); assertFalse(controller.inlineUppercaseActive());
    }
    @Test public void decoderFallbackConsumesSeparatorAndUppercasesOnlyNextDecodeLetter() throws Exception {
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        HintController controller = configuredController();
        assertFalse(controller.inlineUppercaseActive());
        InlineUppercaseDispatcher dispatcher = new InlineUppercaseDispatcher(adapter, controller);
        Event separator = new Event(new Action(75, Intent.DECODE, "'"));
        assertTrue("Decoder entry works even if the outer IME callback never ran", dispatcher.handle(separator, true));
        assertTrue(controller.inlineUppercaseArmed());
        Event first = new Event(new Action(29, Intent.DECODE, "a"));
        assertFalse(dispatcher.handle(first, true)); assertEquals("A", first.actions[0].text);
        assertEquals(Intent.DECODE, first.actions[0].intention);
        assertTrue(dispatcher.acceptsUppercase(first.actions[0], true));
        assertFalse(dispatcher.acceptsUppercase(first.actions[0], false));
        assertFalse(controller.inlineUppercaseArmed());
        assertFalse("Nested decoder hooks must not change the one-shot state again", dispatcher.handle(first, true));
        Event next = new Event(new Action(30, Intent.DECODE, "b"));
        assertFalse(dispatcher.handle(next, true)); assertEquals("b", next.actions[0].text);
        Event enter = new Event(new Action(66, null, null));
        assertFalse("Enter remains on Gboard's own commit path", dispatcher.handle(enter, true));
    }
    @Test public void nonChineseAndNonPressEventsNeverArmOrChangeLetters() throws Exception {
        HintController controller = configuredController();
        InlineUppercaseAdapter adapter = InlineUppercaseAdapter.discover(Processor.class);
        InlineUppercaseDispatcher dispatcher = new InlineUppercaseDispatcher(adapter, controller);
        Event event = new Event(new Action(75, Intent.DECODE, "'"));
        assertFalse(dispatcher.handle(event, false)); assertFalse(controller.inlineUppercaseArmed());
        event.phase = Phase.LONG_PRESS; assertFalse(dispatcher.handle(event, true)); assertFalse(controller.inlineUppercaseArmed());
        event.phase = Phase.PRESS; ReflectionHelpers.setField(controller, "inlineUppercaseEnabled", false);
        assertFalse(dispatcher.handle(event, true)); assertFalse(controller.inlineUppercaseArmed());
    }

}
