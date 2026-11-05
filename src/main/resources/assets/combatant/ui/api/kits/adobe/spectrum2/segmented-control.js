/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSegmentedControl {
  static render(props={}) {
    const key=ui.str(props.key,'s2:segments'),p=SpectrumFoundation.colors(props.colors);
    const items=(Array.isArray(props.items)?props.items:[]).slice(0,5);
    const height=({XS:28,S:32,M:40,L:48,XL:56})[ui.str(props.size,'M')]||40;
    const width=Math.max(1,ui.num(props.width,340));
    const vertical=props.orientation==='vertical';
    const disabled=!!props.disabled;
    const track=props.hideTrack!==true;
    const selected=ui.str(props.selectedId,items.length?ui.str(items[0].id,''):'');
    const count=Math.max(1,items.length);
    const totalHeight=vertical?count*(height+2)+6:height;
    const gap=track?2:8;
    const w=vertical?width:(width-(track?8:0)-gap*(count-1))/count;
    const inset=track?4:0;
    const radius=Math.min(12,height*.26);
    const children=[];
    if(track)children.push(SpectrumFoundation.rect(`${key}:track`,0,0,width,totalHeight,
      radius+3,disabled?p.disabledSurface:p.field,p.border,1));
    items.forEach((item,index)=>{
      const id=ui.str(item.id,String(index)),isSelected=id===selected;
      const itemDisabled=disabled||!!item.disabled;
      const x=vertical?inset:inset+index*(w+gap);
      const y=vertical?inset+index*(height+2):inset;
      const iw=vertical?Math.max(1,width-2*inset):Math.max(1,w);
      const ih=vertical?height:height-2*inset;
      const surface=isSelected?(itemDisabled?p.disabledSurface:p.raised):'#00000000';
      const border=isSelected&&!itemDisabled?p.border:'#00000000';
      const labelColor=itemDisabled?p.disabled:isSelected?p.text:p.muted;
      children.push(ui.button({key:`${key}:item:${id}`,
        class:ui.abs(x,y,iw,ih,SpectrumFoundation.cursor(itemDisabled)),
        stateValues:{selected:isSelected,disabled:itemDisabled},
        stateMotions:{hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'},
          focusVisible:{durationMs:100,easing:'out-cubic'}},
        ...(itemDisabled?{}:{onClick:item.onClick}),children:[
          SpectrumFoundation.rect(`${key}:item:${id}:bg`,0,0,iw,ih,radius,surface,border,isSelected?1:0),
          SpectrumFoundation.rect(`${key}:item:${id}:hover`,0,0,iw,ih,radius,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
              alphaTerms:itemDisabled?{}:{hover:.06,press:.12}}}),
          SpectrumFoundation.text(`${key}:item:${id}:label`,ui.str(item.label,id),8,0,Math.max(1,iw-16),ih,
            {align:'center',size:ih<33?14:16,strong:isSelected,color:labelColor}),
          SpectrumFoundation.rect(`${key}:item:${id}:focus`,-2,-2,iw+4,ih+4,radius+2,
            '#00000000',p.focus,2,{stateSource:'parent',strokeReactive:{base:p.focus,
              alphaBase:0,alphaTerms:itemDisabled?{}:{focusVisible:1}}})
        ]}));
    });
    return ui.stack({key,class:ui.abs(0,0,width,totalHeight),children});
  }
}
