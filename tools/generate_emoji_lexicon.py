#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
"""Compile the manually reviewed Mainland Chinese lexicon; never expand upstream aliases."""
import functools
import hashlib
import itertools
import json
from collections import defaultdict
from pathlib import Path
import re
from pypinyin import pinyin, Style

root = Path(__file__).resolve().parents[1]
source = root / 'tools/reference/emoji'
lexicon = source / 'zh-CN-curated.tsv'
rows = []
for line in lexicon.read_text().splitlines():
    words, emoji = line.split('\t')
    if len(emoji) == 2 and all(0x1f1e6 <= ord(c) <= 0x1f1ff for c in emoji):
        if any(re.search('[A-Za-z]', word) for word in words.split()):
            raise ValueError('Country/region flags must use Chinese names only, without country codes')
    rows.append((words.split(), emoji))
if not 0 < len(rows) <= 1000:
    raise ValueError('The reviewed lexicon must contain 1–1000 rows')
if len({emoji for _, emoji in rows}) != len(rows):
    raise ValueError('Duplicate emoji rows')
# Use the existing externally generated scheme data, not a second set of mappings.
schemes = {}
for name, finals, initials in re.findall(r'register\("([^"\n]+)", "([^"\n]+)", "([^"\n]+)"\)',
                                       (root / 'src/main/java/io/github/rikumi/gboardhints/SchemeData.java').read_text()):
    final_map = defaultdict(list)
    for entry in finals.split():
        key, value = entry.split(':'); final_map[value].append(key)
    for vowel in 'aeiou': final_map[vowel].append(vowel)
    initial_map = {value:key for key,value in (entry.split(':') for entry in initials.split())}
    schemes[name] = (final_map, initial_map)
@functools.lru_cache(None)
def spell(syllable, scheme):
    finals, initials = schemes[scheme]
    if not syllable: return ()
    if syllable[0] in 'aeo':
        if len(syllable) == 1: return (syllable * 2,)
        if len(syllable) == 2: return (syllable,)
        return tuple(syllable[0] + key for key in finals.get(syllable, ()))
    initial = syllable[:2] if syllable[:2] in initials else syllable[0]
    final = syllable[len(initial):]
    prefix = initials.get(initial, initial)
    keys = list(finals.get(final, ()))
    if initial in ('j','q','x','y') and final.startswith('u'):
        keys += finals.get('v' + final[1:], ())
    return tuple(prefix + key for key in dict.fromkeys(keys))
def normalize(text):
    return ''.join(c.lower() for c in text if c.isalnum() or c == ';')
output = root / 'src/main/assets/emoji'
output.mkdir(parents=True, exist_ok=True)
outputs = {}
for scheme in ['full'] + list(schemes):
    lines = []
    for words, emoji in rows:
        exact, initials = set(), set()
        for word in words:
            exact.add(normalize(word))
            if re.fullmatch('[A-Za-z]+', word): continue
            # Use only the normal phrase pronunciation, not every heteronym.
            parts = [item[0].lower().replace('ü', 'v') for item in
                     pinyin(word, style=Style.NORMAL, heteronym=False)]
            if not all(re.fullmatch('[a-z]+', part) for part in parts):
                continue
            exact.add(''.join(parts))
            initials.add(''.join(part[0] for part in parts))
            if scheme != 'full':
                for code in itertools.product(*(spell(part, scheme) for part in parts)):
                    exact.add(''.join(code))
        lines.append(emoji + '\t' + ' '.join(sorted(exact)) + '\t' + ' '.join(sorted(initials)) + '\n')
    path = output / (scheme + '.tsv')
    path.write_text(''.join(lines), encoding='utf-8')
    outputs[scheme] = {'rows':len(lines), 'bytes':path.stat().st_size,
                       'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
revision = hashlib.sha256(''.join(name + outputs[name]['sha256'] for name in sorted(outputs)).encode()).hexdigest()
(output / 'revision.txt').write_text(revision + '\n')
summary = {'locale':'zh-CN', 'emojiCount':len(rows), 'keywords':sum(len(words) for words,_ in rows),
           'source':'tools/reference/emoji/zh-CN-curated.tsv', 'selection':'Manually reviewed everyday Mainland Simplified Chinese vocabulary; Common Chinese country/region short names only; country codes are excluded',
           'matching':'Exact complete spelling or per-character initials; no prefixes, alternate readings or native candidate-word lookup',
           'ranking':'Exact spelling before initials; reviewed source row order resolves collisions. Native Gboard candidates are preserved.',
           'revision':revision, 'pypinyin':'0.55.0',
           'sources':{path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in [lexicon,source/'annotations-zh.xml',source/'annotationsDerived-zh.xml']},
           'indices':outputs}
(root / 'compatibility/emoji-sources.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(summary, ensure_ascii=False, indent=2))
