/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.lang.reflect.*;
import java.util.*;

/** A reversible native tenth key, with native decode/commit actions and equal row weights. */
final class SemicolonKeys {
    private final NativeKeyAdapter adapter;
    private final Map<LinearLayout, Row> rows = new IdentityHashMap<>();
    private final Map<View, Shift> shifts = new WeakHashMap<>();
    private final Set<View> injected = Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    SemicolonKeys(NativeKeyAdapter adapter) { this.adapter = adapter; }
    static boolean decodeSemicolon(boolean composing, String scheme) {
        return composing && SchemeData.finals(scheme).containsKey(';');
    }
    void update(Collection<View> observed, boolean enabled, boolean chinese, boolean pinyin, boolean composing, String scheme) {
        update(observed,enabled,chinese,pinyin,composing,scheme,false);
    }
    void update(Collection<View> observed, boolean enabled, boolean chinese, boolean pinyin, boolean composing, String scheme, boolean uppercase) {
        uppercase = uppercase && composing;
        for (Row row : rows.values()) row.wanted = false;
        Map<View, Map<Character, View>> keyboards = new IdentityHashMap<>();
        try {
            for (View view : observed) {
                if (!view.isShown() || !PrimaryLetterSize.keyboardKey(view)) continue;
                char letter = AdaptiveKeyAdapter.key(view, adapter.metadata);
                View keyboard = keyboard(view);
                if (keyboard != null && letter >= 'a' && letter <= 'z') {
                    Map<Character,View> letters = keyboards.get(keyboard);
                    if (letters == null) { letters = new HashMap<>(); keyboards.put(keyboard,letters); }
                    letters.put(letter,view);
                }
            }
            for (Map.Entry<View, Map<Character, View>> entry : keyboards.entrySet()) {
                View l = entry.getValue().get('l');
                if (l == null) continue;
                for (View view : observed) {
                    if (!view.isShown() || keyboard(view) != entry.getKey()) continue;
                    String name = NativeKeyAdapter.name(view);
                    if (!name.contains("shift") && !name.equals("B00")) continue;
                    Shift shift = shifts.get(view);
                    boolean active = chinese && (uppercase || pinyin && composing);
                    if (active && shift == null) { shift = new Shift(); shifts.put(view,shift); }
                    if (shift != null) shift.apply(view,l,active,uppercase);
                }
                if (enabled && chinese) try {
                    List<View> middle = row(entry.getValue(),"asdfghjkl"), top = row(entry.getValue(),"qwertyuiop");
                    if (middle != null && top != null) {
                        LinearLayout parent = (LinearLayout)l.getParent();
                        Row state = rows.get(parent);
                        if (state == null) {
                            state = new Row(entry.getKey(),parent,middle,top); rows.put(parent,state);
                        }
                        state.wanted = true; state.apply(l,pinyin && decodeSemicolon(composing,scheme));
                    }
                } catch(ReflectiveOperationException | RuntimeException error) {
                    HintsModule.logFailure("semicolon row",error);
                    Row previous=l.getParent() instanceof LinearLayout?rows.get((LinearLayout)l.getParent()):null;
                    if(previous!=null)previous.restore();
                }

            }
            // Mode changes can remove the alphabet before the previous Shift is seen.
            if (!chinese || !uppercase && (!pinyin || !composing)) for (Map.Entry<View, Shift> entry : new ArrayList<>(shifts.entrySet()))
                entry.getValue().apply(entry.getKey(),null,false,false);
            for (Iterator<Map.Entry<LinearLayout,Row>> it = rows.entrySet().iterator(); it.hasNext();) {
                Row state = it.next().getValue();
                if (!state.wanted) state.restore();
                if (!state.parent.isAttachedToWindow()) it.remove();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            HintsModule.logFailure("semicolon key",e);
            restore();
        }
    }
    private List<View> row(Map<Character,View> letters, String sequence) {
        List<View> result = new ArrayList<>(); View parent = null;
        for (char c : sequence.toCharArray()) {
            View view = letters.get(c);
            if (view == null || !(view.getParent() instanceof LinearLayout)) return null;
            if (parent != null && parent != view.getParent()) return null;
            parent = (View)view.getParent(); result.add(view);
        }
        LinearLayout layout = (LinearLayout)parent;
        if (layout.getOrientation() != LinearLayout.HORIZONTAL) return null;
        return result;
    }
    private static View keyboard(View key) {
        for (View view = key; view != null; view = view.getParent() instanceof View ? (View)view.getParent() : null)
            for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
                if (type.getName().equals("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView")) return view;
        return null;
    }
    void restore() {
        for (Row row : rows.values()) try { row.restore(); } catch (RuntimeException e) { HintsModule.logFailure("restore key row",e); }
        for (Map.Entry<View,Shift> entry : new ArrayList<>(shifts.entrySet())) try { entry.getValue().apply(entry.getKey(),null,false,false); }
            catch (ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("restore separator",e); }
        rows.clear(); shifts.clear();
    }
    private final class Shift {
        Object original, applied, template, arrowSource;
        boolean arrow;
        void apply(View view, View l, boolean active, boolean uppercase) throws ReflectiveOperationException {
            Object current = adapter.definition(view);
            if (!active) {
                if (applied != null && current == applied) adapter.bind(view,original);
                original = null; applied = null; template = null; return;
            }
            if (current != applied) original = current;
            if(current!=applied && current!=null) {
                String nativeText=adapter.pressText(current);
                if(AdaptiveKeyAdapter.key(view,adapter.metadata)==0 && !nativeText.equals("'") && !nativeText.equals(";") && !nativeText.equals("；"))arrowSource=current;
            }
            Object source = uppercase && arrowSource!=null ? arrowSource : adapter.definition(l);
            if (applied != null && current == applied && template == source && arrow == uppercase) return;
            applied = adapter.key(source,l,view.getId(),uppercase && arrowSource!=null ? null : uppercase ? "⇧" : "'","'",true);
            template = source; arrow = uppercase;
            adapter.bind(view,applied);
        }
    }
    private final class Row {
        final View keyboard;
        final LinearLayout parent, topParent;
        final List<View> middle, top;
        final View semicolon;
        final Map<View,LinearLayout.LayoutParams> params = new IdentityHashMap<>();
        final Map<View,Integer> visibility = new IdentityHashMap<>();
        final Map<View,int[]> padding = new IdentityHashMap<>();
        final Map<View,NativeKeyFace> normalFaces = new IdentityHashMap<>();
        final float weightSum;
        final int left, right;
        final LinearLayout.LayoutParams rowParams;
        boolean wanted, active, nativeTenth;
        Object originalTenth;
        int originalTenthVisibility;
        Object template, applied;
        long templateFlags;
        boolean decoding;
        Row(View keyboard, LinearLayout parent, List<View> middle, List<View> top) throws ReflectiveOperationException {
            this.keyboard = keyboard; this.parent = parent; this.middle = middle; this.top = top;
            topParent = (LinearLayout)top.get(0).getParent();
            // Do not remove tablet split gaps or existing physical/function keys.
            for (int i = 0; i < topParent.getChildCount(); i++) {
                View child = topParent.getChildAt(i);
                if (!top.contains(child) && adapter.metadata.getDeclaringClass().isInstance(child) && child.isShown()
                        && adapter.definition(child)!=null) throw new IllegalArgumentException("Additional top key");
            }
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                boolean tenth = NativeKeyAdapter.name(child).equals("C10") || NativeKeyAdapter.name(child).equals("key_pos_1_9");
                if (adapter.metadata.getDeclaringClass().isInstance(child) && !middle.contains(child) && !tenth && child.isShown() && adapter.definition(child)!=null)
                    throw new IllegalArgumentException("Extra native key");
                if (injected.contains(child)) continue;
                params.put(child,new LinearLayout.LayoutParams((LinearLayout.LayoutParams)child.getLayoutParams()));
                visibility.put(child,child.getVisibility());
                padding.put(child,new int[]{child.getPaddingLeft(),child.getPaddingTop(),child.getPaddingRight(),child.getPaddingBottom()});
            }
            weightSum = parent.getWeightSum(); left = parent.getPaddingLeft(); right = parent.getPaddingRight();
            rowParams = parent.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams)parent.getLayoutParams()) : null;
            int id = parent.getResources().getIdentifier("C10","id",HintsModule.TARGET);
            if (id == 0) id = parent.getResources().getIdentifier("key_pos_1_9","id",HintsModule.TARGET);
            View existing = id == 0 ? null : keyboard.findViewById(id);
            if (id == 0 || existing != null && existing.getParent() != parent)
                throw new IllegalArgumentException("No free tenth position");
            if (existing != null) {
                semicolon = existing;
                if (!injected.contains(existing)) {
                    Object definition = adapter.definition(existing);
                    String text = adapter.pressText(definition);
                    if (definition != null && !text.equals(";") && !text.equals("；"))
                        throw new IllegalArgumentException("Occupied tenth position");
                    nativeTenth = true; originalTenth = definition; originalTenthVisibility = existing.getVisibility();
                }
                return;
            }
            semicolon = adapter.createLike(top.get(9)); semicolon.setId(id);
            semicolon.setPadding(top.get(9).getPaddingLeft(),top.get(9).getPaddingTop(),top.get(9).getPaddingRight(),top.get(9).getPaddingBottom());
            android.graphics.drawable.Drawable background = top.get(9).getBackground();
            if (background != null && background.getConstantState() != null)
                semicolon.setBackground(background.getConstantState().newDrawable(semicolon.getResources()).mutate());
            semicolon.setMinimumHeight(middle.get(8).getMinimumHeight());
            semicolon.setVisibility(View.GONE);
            parent.addView(semicolon,new LinearLayout.LayoutParams((LinearLayout.LayoutParams)middle.get(8).getLayoutParams()));
            try { adapter.register(keyboard,semicolon); injected.add(semicolon); }
            catch (ReflectiveOperationException | RuntimeException e) { parent.removeView(semicolon); throw e; }
        }
        void apply(View l, boolean decode) throws ReflectiveOperationException {
            View style=top.get(9);
            Object source = adapter.definition(style);
            if (source == null) return;
            if (nativeTenth && adapter.definition(semicolon) != applied) {
                originalTenth = adapter.definition(semicolon); originalTenthVisibility = semicolon.getVisibility();
            }
            long flags = adapter.flags(style);
            if (template != source || decoding != decode || flags != templateFlags || adapter.definition(semicolon) != applied) {
                applied = adapter.key(style,semicolon.getId(),";",decode ? ";" : "；",decode);
                adapter.bind(semicolon,applied,flags);
                template = source; decoding = decode; templateFlags = flags;
            }
            if (active) { align(); semicolon.setVisibility(View.VISIBLE); return; }
            active = true;
            for (View child : params.keySet()) if (!middle.contains(child)) child.setVisibility(View.GONE);
            parent.setPadding(topParent.getPaddingLeft(),parent.getPaddingTop(),topParent.getPaddingRight(),parent.getPaddingBottom());
            parent.setWeightSum(topParent.getWeightSum());
            if (rowParams != null && topParent.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams target = (LinearLayout.LayoutParams)topParent.getLayoutParams();
                LinearLayout.LayoutParams aligned = new LinearLayout.LayoutParams(rowParams);
                aligned.width = target.width; aligned.leftMargin = target.leftMargin; aligned.rightMargin = target.rightMargin; aligned.gravity = target.gravity;
                parent.setLayoutParams(aligned);
            }
            int insert = parent.indexOfChild(middle.get(8)) + 1;
            parent.removeView(semicolon); parent.addView(semicolon,insert,new LinearLayout.LayoutParams((LinearLayout.LayoutParams)top.get(9).getLayoutParams()));
            align();
            semicolon.setVisibility(View.VISIBLE);
            parent.requestLayout(); keyboard.requestLayout();
        }
        private void align() throws ReflectiveOperationException {
            for(int index:new int[]{0,8}) {
                View key=middle.get(index),model=middle.get(index==0?1:7);
                NativeKeyFace face=normalFaces.get(key);
                if(face==null){face=new NativeKeyFace(adapter,key);normalFaces.put(key,face);}
                face.apply(model);
            }
            // Edge touch targets carry half-key padding in native staggered rows.
            // Copy each top column's padding and measured width, not just its weight.
            for(int i=0;i<10;i++) {
                View target=i==9?semicolon:middle.get(i),model=top.get(i);
                if(target.getPaddingLeft()!=model.getPaddingLeft() || target.getPaddingTop()!=model.getPaddingTop()
                        || target.getPaddingRight()!=model.getPaddingRight() || target.getPaddingBottom()!=model.getPaddingBottom())
                    target.setPadding(model.getPaddingLeft(),model.getPaddingTop(),model.getPaddingRight(),model.getPaddingBottom());
                LinearLayout.LayoutParams wanted=new LinearLayout.LayoutParams((LinearLayout.LayoutParams)model.getLayoutParams());
                if(model.getWidth()>0) {wanted.width=model.getWidth();wanted.weight=0;
                    int previous=i==0?topParent.getPaddingLeft():top.get(i-1).getRight()+((LinearLayout.LayoutParams)top.get(i-1).getLayoutParams()).rightMargin;
                    wanted.leftMargin=model.getLeft()-previous;
                }
                LinearLayout.LayoutParams now=(LinearLayout.LayoutParams)target.getLayoutParams();
                if(now.width!=wanted.width || now.height!=wanted.height || now.weight!=wanted.weight
                        || now.leftMargin!=wanted.leftMargin || now.rightMargin!=wanted.rightMargin || now.gravity!=wanted.gravity)
                    target.setLayoutParams(wanted);
            }
        }
        void restore() {
            if (!active) return;
            active = false; template = null;
            if (nativeTenth) {
                try {
                    if (adapter.definition(semicolon) == applied) adapter.bind(semicolon,originalTenth);
                    else { originalTenth = adapter.definition(semicolon); originalTenthVisibility = semicolon.getVisibility(); }
                } catch (ReflectiveOperationException e) { HintsModule.logFailure("restore native tenth key",e); }
                visibility.put(semicolon,originalTenthVisibility);
            }
            semicolon.setVisibility(View.GONE);
            for(NativeKeyFace face:normalFaces.values())try{face.restore();}
                catch(ReflectiveOperationException error){HintsModule.logFailure("restore native face",error);}
            normalFaces.clear();
            for (View child : params.keySet()) { child.setLayoutParams(new LinearLayout.LayoutParams(params.get(child))); if(!middle.contains(child))child.setVisibility(visibility.get(child)); int[] pad=padding.get(child);
                if(pad!=null)child.setPadding(pad[0],pad[1],pad[2],pad[3]); }
            parent.setPadding(left,parent.getPaddingTop(),right,parent.getPaddingBottom()); parent.setWeightSum(weightSum);
            if (rowParams != null) parent.setLayoutParams(new LinearLayout.LayoutParams(rowParams));
            parent.requestLayout(); keyboard.requestLayout();
        }
    }
}
