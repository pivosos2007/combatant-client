/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSelectBoxGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:select-box-group'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(170,ui.num(props.width,550)),items=Array.isArray(props.items)?props.items:[];
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const horizontal=props.orientation!=='vertical',gap=12;
    const columns=horizontal?Math.min(items.length||1,Math.max(1,Math.floor((width+gap)/174))):1;
    const w=(width-gap*(columns-1))/columns,cardH=106;
    const children=[SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Choose an option'),
      0,0,width,28,{size:17,strong:true})];
    items.forEach((item,index)=>{
      const id=ui.str(item.id,`option-${index}`),chosen=selected.includes(id),
        disabled=!!props.disabled||!!item.disabled;
      const x=(index%columns)*(w+gap),y=42+Math.floor(index/columns)*(cardH+gap);
      const nodeKey=`${key}:item:${id}`;
      const border=chosen?p.accent:disabled?p.disabled:p.border;
      const childrenCard=[
        ui.shape({key:`${nodeKey}:surface`,shape:'box',style:ui.absolute(0,0,w,cardH),
          corners:ui.corner.all(ui.corner.rounded(9)),fill:disabled?p.disabledSurface:chosen?p.selected:p.surface,
          stroke:border,strokeWidth:chosen?2:1,interactive:false}),
        ui.shape({key:`${nodeKey}:hover`,shape:'box',style:ui.absolute(0,0,w,cardH),
          corners:ui.corner.all(ui.corner.rounded(9)),fill:'#00000000',interactive:false,
          stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.055,press:.09}}}),
        SpectrumFoundation.rect(`${nodeKey}:mark`,w-31,11,19,19,9.5,
          chosen?p.accent:'#00000000',chosen?p.accent:border,2),
        ...(chosen?[ui.svg({key:`${nodeKey}:check`,asset:'check',tint:p.onAccent,
          interactive:false,class:ui.abs(w-28,14,13,13)})]:[]),
        SpectrumFoundation.text(`${nodeKey}:title`,item.label||id,13,37,w-27,26,
          {size:16,strong:true,color:disabled?p.disabled:p.text}),
        SpectrumFoundation.text(`${nodeKey}:description`,item.description||'',13,65,w-24,32,
          {size:13,color:disabled?p.disabled:p.muted,wrap:true,maxLines:2})
      ];
      childrenCard.push(ui.shape({key:`${nodeKey}:focus`,shape:'box',
        style:ui.absolute(-2,-2,w+4,cardH+4),corners:ui.corner.all(ui.corner.rounded(11)),
        fill:'#00000000',stroke:p.focus,strokeWidth:2,interactive:false,
        stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,
          alphaTerms:disabled?{}:{focusVisible:1}}}));
      children.push(ui.button({key:nodeKey,class:ui.abs(x,y,w,cardH,SpectrumFoundation.cursor(disabled)),
        stateValues:{selected:chosen,disabled},stateMotions:{hover:{durationMs:125,easing:'out-cubic'},
          focusVisible:{durationMs:100,easing:'out-cubic'}},
        ...(disabled?{}:{onClick:item.onClick}),children:childrenCard}));
    });
    const rows=Math.ceil(items.length/columns);
    return SpectrumFoundation.surface(key,width,42+rows*cardH+Math.max(0,rows-1)*gap,children);
  }
}
