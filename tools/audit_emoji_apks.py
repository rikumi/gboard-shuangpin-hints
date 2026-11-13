#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
import sys,json,zipfile,argparse
from pathlib import Path
from analyze_apks import Dex
parser=argparse.ArgumentParser(description='Static emoji hook audit; does not verify device behavior or APK signatures.')
parser.add_argument('apks',nargs='+',type=Path);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
reports=[]
for apk in args.apks:
 name=apk.name
 classes={}
 with zipfile.ZipFile(apk) as z:
  for n in z.namelist():
   if n.endswith('.dex'):classes.update(Dex(z.read(n)).classes)
 hmm=classes['Lcom/google/android/apps/inputmethod/libs/hmm/AbstractHmmDecodeProcessor;'];base=classes[hmm['parent']]
 candidates=set()
 for m in base['methods']:
  if len(m['args'])==2 and m['args'][1]=='Z' and m['return']=='Z':
   for f in classes.get(m['args'][0],{}).get('fields',[]):
    if 'EMOJI' in classes.get(f['type'],{}).get('enumNames',[]):candidates.add(m['args'][0])
 assert len(candidates)==1,(name,candidates)
 candidate=classes[next(iter(candidates))]
 builders=[m for m in candidate['methods'] if m['name']=='<init>' and len(m['args'])==1 and any(c['name']=='<init>' and not c['args'] for c in classes.get(m['args'][0],{}).get('methods',[]))]
 assert len(builders)==1,(name,builders)
 commit=[m for m in hmm['methods'] if m['args']==['Ljava/lang/String;','I','Z'] and m['return']=='V' and not m['access']&8]
 assert len(commit)==1,(name,commit)
 def assignable(target,child):
  if target==child or target=="Ljava/lang/Object;":return True
  d=classes.get(child,{})
  return any(assignable(target,p) for p in [d.get('parent')]+d.get('interfaces',[]) if p and p!='Ljava/lang/Object;')
 feeds=[]
 for m in base['methods']:
  for p in m['args']:
   for f in classes.get(p,{}).get('methods',[]):
    a=f['args']
    if f['access']&8 and f['return']==p and len(a)==4 and a[0]=='Ljava/util/List;' and a[1]==candidate['name'] and a[2]=='Z' and assignable(a[3],base['name']) and f not in feeds:feeds.append(f)
 assert len(feeds)==1,(name,feeds)
 reports.append({'file':name,'candidate':candidate['name'],'builder':builders[0],'publication':feeds[0],'commit':commit[0],'verification':'static APK structure only; not an on-device test'})
 print(name,'PASS: emoji candidate/publication/commit shape')
args.output.write_text(json.dumps(reports,indent=2)+'\n')
