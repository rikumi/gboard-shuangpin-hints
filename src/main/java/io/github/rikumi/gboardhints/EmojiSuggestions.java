/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import io.github.rikumi.gboardhints.bridge.*;

/** Adds native emoji candidates, keeping native selection, composition replacement and reset semantics. */
final class EmojiSuggestions {
    private final HintController controller;
    private final Class<?> chinese;
    private final EnginePreeditAdapter preedit;
    private final NativeEmojiAdapter adapter;
    private final Map<Object,Choice> choices=Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Method> selectionHooks=new HashSet<>();
    private volatile WeakReference<Object> lastOwner=new WeakReference<>(null);
    private final Runnable loaded=this::refresh;
    private static final class Choice {
        final String emoji, profile;
        final long query;
        final WeakReference<Object> owner;
        Choice(String emoji,String profile,String raw,Object owner) { this.emoji=emoji;this.profile=profile;query=fingerprint(raw);this.owner=new WeakReference<>(owner); }
    }
    EmojiSuggestions(Class<?> base,Class<?> hmm,Class<?> chinese,HintController controller) throws ReflectiveOperationException {
        this.controller=controller;this.chinese=chinese;preedit=EnginePreeditAdapter.discover(chinese);
        if(preedit==null)throw new NoSuchMethodException("Chinese preedit");
        adapter=new NativeEmojiAdapter(base,hmm);
    }
    void install() {
        watchDecoder(chinese);
        XposedBridge.hookMethod(adapter.publication,new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object owner=p.args[3];
                if(!chinese.isInstance(owner) || !controller.emojiAllowed())return;
                try { p.args[0]=augment(owner,(List<?>)p.args[0]); }
                catch(ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("emoji suggestions",e); }
            }
        });
    }
    List<?> augment(Object owner,List<?> nativeCandidates) throws ReflectiveOperationException {
        if(!chinese.isInstance(owner) || !controller.emojiAllowed() || nativeCandidates==null || nativeCandidates.isEmpty())return nativeCandidates;
        Context context=controller.context();if(context==null)return nativeCandidates;
        CharSequence preeditText=preedit.read(owner);
        String query=EmojiLexicon.normalize(preeditText);
        if(query.isEmpty())return nativeCandidates;
        lastOwner=new WeakReference<>(owner);
        String profile=EmojiLexicon.profile(controller.emojiScheme());
        EmojiLexicon lexicon=EmojiLexicon.request(context,profile,loaded);
        if(lexicon==null)return nativeCandidates;
        Set<String> displayed=new HashSet<>();
        for(Object candidate:nativeCandidates) {
            String text=adapter.text(candidate);displayed.add(canonical(text));
        }
        List<String> matches=lexicon.match(preeditText);
        if(matches.isEmpty())return nativeCandidates;
        String best=matches.get(0);
        if(displayed.contains(canonical(best)))return nativeCandidates;
        Object candidate=adapter.emoji(nativeCandidates.get(0),best);
        choices.put(candidate,new Choice(best,profile,query,owner));
        List<Object> result=new ArrayList<>(nativeCandidates.size()+1);
        result.add(nativeCandidates.get(0));result.add(candidate);result.addAll(nativeCandidates.subList(1,nativeCandidates.size()));
        return result;
    }
    synchronized void watchDecoder(Class<?> decoder) {
        for(Class<?> type=decoder;type!=null;type=type.getSuperclass()) for(Method method:type.getDeclaredMethods()) {
            Class<?>[] args=method.getParameterTypes();
            if(Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()) || method.getReturnType()!=boolean.class
                    || args.length<1 || args[0]!=adapter.candidateType || args.length>2 || args.length==2 && args[1]!=boolean.class
                    || !selectionHooks.add(method))continue;
            try { XposedBridge.hookMethod(method,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Choice choice=choices.get(p.args[0]);if(choice==null)return;
                    // Single-argument operations delete/reject candidates rather than selecting them.
                    if(p.args.length==1) { p.setResult(false);return; }
                    select(p.thisObject,p.args[0],Boolean.TRUE.equals(p.args[1]));
                    p.setResult(true);
                }
            }); } catch(RuntimeException e) { HintsModule.logFailure("emoji selection hook",e); }
        }
    }
    boolean select(Object owner,Object candidate,boolean commit) {
        Choice choice=choices.get(candidate);if(choice==null)return false;
        if(commit)try {
            String query=EmojiLexicon.normalize(preedit.read(owner));
            if(controller.emojiAllowed() && chinese.isInstance(owner) && choice.owner.get()==owner && choice.query==fingerprint(query) && !query.isEmpty()
                    && choice.profile.equals(EmojiLexicon.profile(controller.emojiScheme()))) {
                adapter.commit(owner,choice.emoji);controller.engineComposition(null);
            }
        } catch(ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("emoji selection",e); }
        return true;
    }
    private static long fingerprint(String value) {
        long hash=0xcbf29ce484222325L;
        for(int i=0;i<value.length();i++) { hash^=value.charAt(i);hash*=0x100000001b3L; }
        return hash;
    }
    private void refresh() {
        Object owner=lastOwner.get();if(owner==null || !controller.emojiAllowed())return;
        try {
            if(!EmojiLexicon.normalize(preedit.read(owner)).isEmpty()) preedit.callbacks.get(0).invoke(owner,0L);
        } catch(ReflectiveOperationException | RuntimeException e) { HintsModule.logFailure("emoji refresh",e); }
    }
    private static String canonical(String value) { return value.replace("\ufe0f","").replace("\ufe0e",""); }
}
