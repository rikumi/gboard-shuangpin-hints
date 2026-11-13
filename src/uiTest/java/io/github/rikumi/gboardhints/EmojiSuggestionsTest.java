/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class EmojiSuggestionsTest {
    public enum Kind { RECOMMENDATION,RAW,EMOJI }
    public static final class Builder { public Builder() { } }
    public static final class Candidate {
        public final CharSequence annotation, display;
        public final String description;
        public final Kind kind;
        public final Object data;
        public Candidate(Builder builder) { annotation="annotation";display="原生候选";description="原生候选";kind=Kind.RECOMMENDATION;data=0; }
        @Override public String toString() { return "Candidate : text = '"+display+"' : rank = 0"; }
    }
    public static final class Command {
        public static Command publish(List<Candidate> words,Candidate top,boolean more,Base owner) { return new Command(); }
    }
    public static class Base {
        protected boolean choose(Candidate word,boolean commit) { return true; }
        public boolean dispatch(Command command,int mode) { return true; }
    }
    public static class Hmm extends Base {
        public String committed;
        protected final void commit(String text,int cursor,boolean reset) { committed=text; ((Chinese)this).raw=""; }
    }
    public static class Chinese extends Hmm {
        public volatile CharSequence raw="";
        protected CharSequence read() { return raw; }
        public void update(long flags) { }
    }
    private HintController controller;
    private InputMethodService service;
    private EmojiSuggestions feature;
    private Chinese owner;
    private NativeEmojiAdapter adapter;
    private Map<String,EmojiLexicon> indices;
    @Before public void setup() throws Exception {
        service=Robolectric.buildService(InputMethodService.class).create().get();
        controller=new HintController();
        ReflectionHelpers.setField(controller,"service",service);
        ReflectionHelpers.setField(controller,"flexibleEmoji",true);
        ReflectionHelpers.setField(controller,"visible",true);
        ReflectionHelpers.setField(controller,"chineseActive",true);
        ReflectionHelpers.setField(controller,"pinyinActive",true);
        ReflectionHelpers.setField(controller,"automatic","XIAOHE");
        feature=new EmojiSuggestions(Base.class,Hmm.class,Chinese.class,controller);
        adapter=new NativeEmojiAdapter(Base.class,Hmm.class); owner=new Chinese();
        indices=ReflectionHelpers.getStaticField(EmojiLexicon.class,"cache");
        try(Reader reader=new InputStreamReader(service.getAssets().open("emoji/XIAOHE.tsv"),"UTF-8")) { indices.put("XIAOHE",new EmojiLexicon(reader)); }
    }
    @After public void cleanup() { indices.clear();service.onDestroy(); }
    @Test public void completeExternalIndexCoversRequestedExamplesAndPreservesNativeFirstCandidate() throws Exception {
        for(String[] sample:new String[][]{{"bh","👍"},{"ui","💩"},{"taiwan","🇹🇼"}}) {
            owner.raw=sample[0];Candidate nativeWord=new Candidate(new Builder());
            List<?> result=feature.augment(owner,Collections.singletonList(nativeWord));
            assertSame(nativeWord,result.get(0));assertTrue(result.size()>1);
            assertTrue(indices.get("XIAOHE").match(sample[0]).contains(sample[1]));
            assertEquals(2,result.size());
            assertEquals(sample[1],adapter.text(result.get(1)));
            assertEquals(Kind.EMOJI,((Candidate)result.get(1)).kind);
            assertEquals("原生候选",nativeWord.display);assertEquals(Kind.RECOMMENDATION,nativeWord.kind);
        }
    }
    @Test public void bestNativeEmojiIsNotDuplicatedOrReplacedBySecondMatch() throws Exception {
        owner.raw="bh";
        Candidate first=new Candidate(new Builder());
        Candidate existing=(Candidate)adapter.emoji(first,"👍");
        List<Candidate> original=Arrays.asList(first,existing);
        assertSame(original,feature.augment(owner,original));
    }
    @Test public void selectingNativeEmojiReplacesCompositionAndUsesNativeReset() throws Exception {
        owner.raw="bh";
        List<?> result=feature.augment(owner,Collections.singletonList(new Candidate(new Builder())));
        Object thumb=null;
        for(Object candidate:result)if(adapter.text(candidate).equals("👍"))thumb=candidate;
        assertNotNull(thumb);
        assertTrue(feature.select(owner,thumb,false));assertNull(owner.committed);assertEquals("bh",owner.raw);
        assertTrue(feature.select(owner,thumb,true));assertEquals("👍",owner.committed);assertEquals("",owner.raw);
    }
    @Test public void staleEmojiCandidateCannotConsumeNewPreedit() throws Exception {
        owner.raw="bh";List<?> result=feature.augment(owner,Collections.singletonList(new Candidate(new Builder())));
        owner.raw="ui";
        assertTrue(feature.select(owner,result.get(1),true));assertNull(owner.committed);assertEquals("ui",owner.raw);
    }
    @Test public void disabledEnglishAndPasswordContextsKeepOriginalList() throws Exception {
        owner.raw="bh";List<?> original=Collections.singletonList(new Candidate(new Builder()));
        ReflectionHelpers.setField(controller,"flexibleEmoji",false);assertSame(original,feature.augment(owner,original));
        ReflectionHelpers.setField(controller,"flexibleEmoji",true);
        ReflectionHelpers.setField(controller,"chineseActive",false);assertSame(original,feature.augment(owner,original));
        ReflectionHelpers.setField(controller,"chineseActive",true);
        ReflectionHelpers.setField(controller,"password",true);assertSame(original,feature.augment(owner,original));
    }
    @Test public void fullPinyinAndInitialsUseExactWholeQuery() throws Exception {
        EmojiLexicon index=indices.get("XIAOHE");
        assertTrue(index.match("bang").contains("👍"));
        assertTrue(index.match("t w").contains("🇹🇼"));
        assertTrue(index.match("").isEmpty());
        assertEquals("tw",EmojiLexicon.normalize("t\u2009w\u0085"));
    }
    @Test public void exactQueryNeverUsesPrefixesOrNativeCorrectedWordsAndPreservesNativeEmoji() throws Exception {
        indices.put("XIAOHE",new EmojiLexicon(new StringReader("ui\t💩\ntaiwan\t🇹🇼\n")));
        Candidate word=new Candidate(new Builder());
        ReflectionHelpers.setField(word,"display","屎");
        Candidate pig=(Candidate)adapter.emoji(word,"🐖");
        Candidate flag=(Candidate)adapter.emoji(word,"🇹🇼");
        List<Candidate> nativeList=Arrays.asList(word,pig,flag);
        for(String raw:new String[]{"u","tai","uix","taiwanx","unmatched"}) {
            owner.raw=raw;
            assertSame(nativeList,feature.augment(owner,nativeList));
        }
        owner.raw="u\u2009i";
        List<?> augmented=feature.augment(owner,nativeList);
        assertEquals(4,augmented.size());
        assertSame(word,augmented.get(0));assertEquals("💩",adapter.text(augmented.get(1)));
        assertSame(pig,augmented.get(2));assertSame(flag,augmented.get(3));
        assertFalse(feature.select(owner,pig,true));assertNull(owner.committed);
    }

    @Test public void reviewedDictionaryKeepsCommonMainlandMeaningsAndCountryNames() throws Exception {
        EmojiLexicon index=indices.get("XIAOHE");
        assertEquals("💩",index.match("ui").get(0));
        assertFalse(index.match("ui").contains("🐖"));
        assertEquals("🐖",index.match("zhu").get(0));
        assertEquals("🐖",index.match("vu").get(0));
        assertTrue(index.match("豕").isEmpty());
        assertTrue(index.match("台灣").isEmpty());
        assertFalse(index.match("CN").contains("🇨🇳"));
        assertFalse(index.match("US").contains("🇺🇸"));
        assertFalse(index.match("HK").contains("🇭🇰"));
        assertFalse(index.match("AE").contains("🇦🇪"));
        assertEquals("🇨🇳",index.match("中国").get(0));
        assertEquals("🇺🇸",index.match("美国").get(0));
        assertEquals("🇭🇰",index.match("香港").get(0));
        assertEquals("🇦🇪",index.match("阿联酋").get(0));
        assertTrue(index.match("阿拉伯联合酋长国").isEmpty());
        assertTrue(index.match("中国香港特别行政区").isEmpty());
        assertTrue(index.match("xianggang").contains("🇭🇰"));
        assertTrue(index.match("xg").contains("🇭🇰"));
        assertTrue(index.match("alianqiu").contains("🇦🇪"));
        assertTrue(index.match("alq").contains("🇦🇪"));
        // TW still matches the initials of 台湾, independently of any country code.
        assertTrue(index.match("tw").contains("🇹🇼"));
        for(String profile:Arrays.asList("full","XIAOHE","Ziranma","MS","ABC","Ziguang","PinyinJiaJia")) {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                    service.getAssets().open("emoji/"+profile+".tsv"),"UTF-8"))) {
                int rows=0;while(reader.readLine()!=null)rows++;
                assertTrue(profile+" is limited to 1000 rows",rows>0 && rows<=1000);
            }
        }
    }

}
