/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumListView {
  static render(props={}) {
    const key=ui.str(props.key,'s2:list-view'),p=SpectrumFoundation.colors(props.colors);
    const w=Math.max(180,ui.num(props.width,420));
    const items=Array.isArray(props.items)?props.items:[];
    const mode=ui.str(props.selectionMode,'multiple');
    const style=ui.str(props.selectionStyle,'highlight');
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const rowH=props.density==='compact'?47:props.density==='spacious'?75:62;
    const h=12+rowH*items.length;
    const children=[];
    if(!props.quiet)children.push(SpectrumFoundation.rect(`${key}:background`,0,0,w,h,10,p.surface,p.border,1));
    items.forEach((item,i)=>{
      const id=ui.str(item.id,String(i)),disabled=!!props.disabled||!!item.disabled;
      const checked=mode!=='none'&&selected.includes(id);
      const ry=6+i*rowH,rx=6,rw=w-12;
      const hasDescription=!!item.description;
      const indicator=style==='checkbox'&&mode!=='none';
      const textX=indicator?47:item.icon?46:15;
      const rowChildren=[
        SpectrumFoundation.rect(`${key}:item:${id}:bg`,0,0,rw,rowH-2,7,
          checked?(style==='highlight'?p.selected:'#00000000'):'#00000000',undefined,0,
          {stateSource:'parent',fillReactive:{base:p.accent,alphaBase:checked&&style==='highlight'?.06:0,
            alphaTerms:disabled?{}:{hover:.08,press:.14}}}),
        SpectrumFoundation.text(`${key}:item:${id}:title`,ui.str(item.label,id),textX,
          hasDescription?7:0,rw-textX-14,hasDescription?26:rowH-3,
          {size:ui.num(props.titleSize,16),strong:checked,color:disabled?p.disabled:p.text}),
        ...(!indicator&&item.icon?[ui.svg({key:`${key}:item:${id}:icon`,asset:ui.str(item.icon,'layers'),
          tint:disabled?p.disabled:checked?p.accent:p.muted,interactive:false,
          class:ui.abs(15,Math.max(5,(rowH-23)/2),22,22)})]:[]),
        ...(hasDescription?[SpectrumFoundation.text(`${key}:item:${id}:description`,item.description,
          textX,31,rw-textX-12,23,{size:ui.num(props.descriptionSize,13),color:disabled?p.disabled:p.muted})]:[])
      ];
      if(indicator){
        rowChildren.push(SpectrumFoundation.rect(`${key}:item:${id}:check`,14,(rowH-22)/2,18,18,4,
          checked?p.accent:'#00000000',disabled?p.disabled:checked?p.accent:p.border,1.5));
        if(checked)rowChildren.push(ui.svg({key:`${key}:item:${id}:check:mark`,asset:'check',
          tint:p.onAccent,interactive:false,class:ui.abs(15,(rowH-22)/2+1,16,16)}));
      }
      children.push(ui.button({key:`${key}:item:${id}`,class:ui.abs(rx,ry,rw,rowH-2,
        SpectrumFoundation.cursor(disabled)),stateValues:{selected:checked,disabled},
        stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
        ...(disabled||mode==='none'?{}:{onClick:item.onClick}),children:rowChildren}));
      if(i<items.length-1)children.push(SpectrumFoundation.rect(`${key}:separator:${id}`,
        18,ry+rowH-2,w-36,1,0,p.border));
    });
    return ui.stack({key,class:ui.abs(0,0,w,h),children});
  }
}
