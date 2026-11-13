/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.*;
import java.util.*;

/** Keeps the physical native key and its events, replacing only the edge-specific presentation. */
final class NativeKeyFace {
    private final NativeKeyAdapter adapter;
    private final ViewGroup key;
    private final List<View> xml=new ArrayList<>();
    private final Map<Field,Object> constructorStyle=new HashMap<>();
    private final int minWidth,minHeight;
    private final float scaleX,scaleY,elevation;
    private final boolean clipChildren,clipPadding;
    private final Drawable background,foreground;
    private final ColorStateList tint;
    private View originalHost;
    private Object original,applied,modelDefinition;
    private boolean active;
    NativeKeyFace(NativeKeyAdapter adapter,View key) {
        this.adapter=adapter;this.key=(ViewGroup)key;
        minWidth=key.getMinimumWidth();minHeight=key.getMinimumHeight();
        scaleX=key.getScaleX();scaleY=key.getScaleY();elevation=key.getElevation();
        background=key.getBackground();foreground=key.getForeground();tint=key.getBackgroundTintList();
        clipChildren=this.key.getClipChildren();clipPadding=this.key.getClipToPadding();
    }
    void apply(View model) throws ReflectiveOperationException {
        Object current=adapter.definition(key),face=adapter.definition(model);
        if(current==null || face==null)return;
        if(current!=applied)original=current;
        if(!active) {
            active=true;
            originalHost=adapter.content(key);
            if(originalHost!=key)for(int i=0;i<key.getChildCount();i++)xml.add(key.getChildAt(i));
            ViewGroup donor=(ViewGroup)adapter.createLike(model);
            adapter.bind(donor,null);
            View host=adapter.content(donor);
            adapter.bind(key,null);
            key.removeAllViews();
            while(donor.getChildCount()>0){View child=donor.getChildAt(0);donor.removeView(child);key.addView(child);}
            adapter.content(key,host==donor?key:host);
            for(Field field:adapter.metadata.getDeclaringClass().getDeclaredFields())if(Modifier.isPublic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive()) {
                field.setAccessible(true);constructorStyle.put(field,field.get(key));field.set(key,field.get(donor));
            }
            key.setMinimumWidth(donor.getMinimumWidth());key.setMinimumHeight(donor.getMinimumHeight());
            key.setBackground(copy(donor.getBackground(),key));key.setBackgroundTintList(donor.getBackgroundTintList());
            key.setForeground(copy(donor.getForeground(),key));key.setElevation(donor.getElevation());
            key.setClipChildren(donor.getClipChildren());key.setClipToPadding(donor.getClipToPadding());
            key.setScaleX(1);key.setScaleY(1);
            current=null;
        }
        if(current==applied && face==modelDefinition)return;
        Object normalized=adapter.normalFace(original,model);
        adapter.bind(key,normalized);applied=normalized;modelDefinition=face;
        key.requestLayout();
    }
    void restore() throws ReflectiveOperationException {
        if(!active)return;
        Object current=adapter.definition(key),nativeDefinition=current==applied?original:current;
        adapter.bind(key,null);key.removeAllViews();
        for(View child:xml){if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);key.addView(child);}
        adapter.content(key,originalHost);
        for(Map.Entry<Field,Object> field:constructorStyle.entrySet())field.getKey().set(key,field.getValue());
        key.setMinimumWidth(minWidth);key.setMinimumHeight(minHeight);
        key.setBackground(background);key.setBackgroundTintList(tint);key.setForeground(foreground);key.setElevation(elevation);
        key.setClipChildren(clipChildren);key.setClipToPadding(clipPadding);key.setScaleX(scaleX);key.setScaleY(scaleY);
        adapter.bind(key,nativeDefinition);
        active=false;xml.clear();constructorStyle.clear();applied=null;modelDefinition=null;key.requestLayout();
    }
    private static Drawable copy(Drawable drawable,View view) {
        return drawable==null?null:drawable.getConstantState()==null?drawable:drawable.getConstantState().newDrawable(view.getResources()).mutate();
    }
}
