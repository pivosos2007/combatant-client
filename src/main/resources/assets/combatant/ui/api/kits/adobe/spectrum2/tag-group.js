/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumTagGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:tag-group'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(96,ui.num(props.width,430)),items=Array.isArray(props.items)?props.items:[];
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const size=ui.str(props.size,'M').toUpperCase();
    const h=({S:25,M:29,L:34})[size]||29,gap=7;
    const mode=ui.str(props.selectionMode,'none'),maxRows=Math.max(1,ui.num(props.maxRows,100));
    const children=[SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Tags'),0,0,width,30,
      {size:17,strong:true})];
    let x=0,y=42,row=1,remaining=0;
    for(let i=0;i<items.length;i++) {
      const item=items[i],id=ui.str(item.id,`tag-${i}`),label=ui.str(item.label,id);
      const removable=!!item.removable&&!item.disabled;
      const w=Math.min(width,Math.max(50,label.length*7.6+21+(removable?24:0)));
      if(x>0&&x+w>width){x=0;y+=h+gap;row++;}
      if(row>maxRows){remaining=items.length-i;y-=h+gap;break;}
      const active=selected.includes(id),disabled=!!item.disabled;
      const color=disabled?p.disabled:active&&props.emphasized?p.onAccent:p.text;
      const fill=disabled?p.disabledSurface:active?(props.emphasized?p.accent:p.selected):p.raised;
      const tkey=`${key}:item:${id}`;
      children.push(ui.button({key:tkey,class:ui.abs(x,y,w,h,
        disabled?SpectrumFoundation.CURSOR_UNSUPPORTED:mode==='none'?'cursor-default':'cursor-pointer'),
        stateValues:{selected:active,disabled},
        stateMotions:{hover:{durationMs:120,easing:'out-cubic'},focusVisible:{durationMs:110,easing:'out-cubic'}},
        ...(!disabled&&mode!=='none'?{onClick:item.onSelect}:{}),children:[
          SpectrumFoundation.rect(`${tkey}:surface`,0,0,w,h,6,
            fill,active?p.accent:p.border,1),
          SpectrumFoundation.text(`${tkey}:label`,label,8,0,w-16-(removable?21:0),h,
            {size:size==='S'?13:14,color,align:'center'}),
          SpectrumFoundation.rect(`${tkey}:focus`,-2,-2,w+4,h+4,8,
            '#00000000',p.focus,2,{stateSource:'parent',strokeReactive:{base:p.focus,
              alphaBase:0,alphaTerms:disabled?{}:{focusVisible:1}}})
        ]}));
      if(removable){
        const rk=`${key}:remove:${id}`;
        children.push(ui.button({key:rk,class:ui.abs(x+w-23,y,23,h,'cursor-pointer'),
          onClick:item.onRemove,children:[
            ui.svg({key:`${rk}:icon`,asset:'x',tint:p.text,interactive:false,
              class:ui.abs(5,(h-13)/2,13,13)})
          ]}));
      }
      x+=w+gap;
    }
    if(remaining)children.push(SpectrumFoundation.text(`${key}:overflow`,
      `+${remaining} more`,0,y+h+6,width,23,{size:14,color:p.muted}));
    const total=y+h+(remaining?30:0)+12;
    if(props.description)children.push(SpectrumFoundation.text(`${key}:description`,props.description,
      0,total,width,26,{size:14,color:p.muted}));
    return SpectrumFoundation.surface(key,width,total+(props.description?30:0),children);
  }
}
