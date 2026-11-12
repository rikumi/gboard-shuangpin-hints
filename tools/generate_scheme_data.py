#!/usr/bin/env python3
"""Generate Java tables from upstream libime reference files; no APK dependency."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
data_dir = pathlib.Path(sys.argv[1])
source = (data_dir / 'shuangpindata.h').read_text()
syllables = sorted(set(re.findall(r'\{"([a-z]+)", PinyinInitial::\w+, PinyinFinal::\w+, PinyinFuzzyFlag::None\}',
                                (data_dir / 'pinyindata.cpp').read_text())))
assert len(syllables) > 400
schemes = ['XIAOHE', 'Ziranma', 'MS', 'ABC', 'Ziguang', 'PinyinJiaJia']
out = '''/*
 * Generated from fcitx/libime shuangpindata.h and pinyindata.cpp.
 * Copyright 2011-2020 CSSlayer <wengxt@gmail.com>
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package io.github.rikumi.gboardhints;

import java.util.*;

final class SchemeData {
    private static final Map<String, Map<Character, List<String>>> FINALS = new HashMap<>();
    private static final Map<String, Map<Character, String>> INITIALS = new HashMap<>();
    static final Set<String> SYLLABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "''' + ' '.join(syllables) + '''".split(" "))));
    static {
'''
for scheme in schemes:
    finals = re.search(rf'SPMap_C_{scheme}\[\] = \{{(.*?)\}};', source, re.S).group(1)
    initials = re.search(rf'SPMap_S_{scheme}\[\] = \{{(.*?)\}};', source, re.S).group(1)
    final_pairs = re.findall(r'\{"([a-z]+)",\s*\'(.)\'\}', finals)
    initial_pairs = re.findall(r'\{"([a-z]+)",\s*\'(.)\'\}', initials)
    assert len(final_pairs) >= 27 and len(initial_pairs) == 3
    out += f'        register("{scheme}", "' + ' '.join(k + ':' + v for v, k in final_pairs) + '", "' + ' '.join(k + ':' + v for v, k in initial_pairs) + '");\n'
out += '''    }
    private static void register(String name, String finals, String initials) {
        Map<Character, List<String>> f = new LinkedHashMap<>();
        for (String entry : finals.split(" ")) {
            char key = entry.charAt(0);
            if (!f.containsKey(key)) f.put(key, new ArrayList<>());
            f.get(key).add(entry.substring(2));
        }
        for (char key : "aeiou".toCharArray()) {
            if (!f.containsKey(key)) f.put(key, new ArrayList<>());
            f.get(key).add(String.valueOf(key));
        }
        Map<Character, String> i = new HashMap<>();
        for (String entry : initials.split(" ")) i.put(entry.charAt(0), entry.substring(2));
        FINALS.put(name, f); INITIALS.put(name, i);
    }
    static Map<Character, List<String>> finals(String scheme) {
        Map<Character, List<String>> result = FINALS.get(scheme);
        return result == null ? Collections.emptyMap() : result;
    }
    static String initial(String scheme, char key) {
        Map<Character, String> result = INITIALS.get(scheme);
        return result == null ? null : result.get(Character.toLowerCase(key));
    }
}
'''
(root / 'src/main/java/io/github/rikumi/gboardhints/SchemeData.java').write_text(out)
print('Generated', len(schemes), 'schemes and', len(syllables), 'syllables')
