/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumActionButton } from './action-button.js';

export class SpectrumActionBar {
  static render(props={}) {
    const key=ui.str(props.key,'s2:action-bar');
    const p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(240,ui.num(props.width,560));
    const count=props.selectedItemCount==='all'?'all':Math.max(0,Math.floor(ui.num(props.selectedItemCount,0)));
    if(count===0)return SpectrumFoundation.surface(key,width,0,[]);
    const compact=width<540;
    const emphasized=!!props.emphasized;
    const h=compact?112:60;
    const bg=emphasized?p.accent:p.raised;
    const fg=emphasized?p.onAccent:p.text;
    const items=(Array.isArray(props.items)?props.items:[]).slice(0,compact?3:4);
    const size=compact?34:36, gap=6;
    const actionsWidth=width-32;
    const preferredWidth=Math.min(compact?112:100,
      Math.max(68,(actionsWidth-gap*Math.max(0,items.length-1))/Math.max(1,items.length)));
    const actionStart=compact?16:width-16-items.length*preferredWidth-Math.max(0,items.length-1)*gap;
    const countWidth=compact?width-63:Math.max(0,actionStart-52);
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,width,h,12,bg,emphasized?undefined:p.border,emphasized?0:1),
      SpectrumFoundation.text(`${key}:count`,count==='all'?'All selected':`${count} selected`,46,10,
        countWidth,compact?34:h-20,{size:16,strong:true,color:fg}),
      ui.button({key:`${key}:clear`,class:ui.abs(8,10,32,34,SpectrumFoundation.cursor(false)),
        onClick:props.onClear,children:[
          SpectrumFoundation.rect(`${key}:clear:hover`,0,1,32,32,6,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:fg,alphaBase:0,alphaTerms:{hover:.12,press:.22}}}),
          ui.svg({key:`${key}:clear:icon`,asset:'x',tint:fg,
            class:ui.abs(7,8,18,18),interactive:false})
        ],stateMotions:{hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}}}),
    ];
    let x=actionStart;
    const y=compact?61:12;
    for(let i=0;i<items.length;i++){
      const item=items[i],id=ui.str(item.id,`action-${i}`);
      const node=SpectrumActionButton.render({key:`${key}:action:${id}`,label:item.label,
        width:preferredWidth,size:'M',quiet:true,disabled:!!item.disabled,
        onClick:item.onClick});
      children.push({...node,style:ui.absolute(x,y,preferredWidth,size)});
      x+=preferredWidth+gap;
    }
    return SpectrumFoundation.surface(key,width,h,children);
  }
}
