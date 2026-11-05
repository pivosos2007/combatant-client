/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSideNavigation {
  static render(props={}) {
    const key=ui.str(props.key,'s2:side-nav'),p=SpectrumFoundation.colors(props.colors);
    const items=Array.isArray(props.items)?props.items:[],w=Math.max(200,ui.num(props.width,320));
    const children=[];let yy=0;
    const add=(item,level)=>{
      const id=ui.str(item.id,'item'),disabled=!!item.disabled;
      if(item.type==='header'){
        children.push(SpectrumFoundation.text(`${key}:${id}:header`,item.label||'',12,yy+7,w-24,26,
          {size:14,strong:true,color:p.muted}));yy+=42;return;
      }
      const h=42,selected=item.id===props.selected;
      const indent=level*21,hasChildren=Array.isArray(item.children)&&item.children.length>0;
      const nodeKey=`${key}:item:${id}`;
      children.push(ui.button({key:nodeKey,class:ui.abs(indent,yy,w-indent,h,SpectrumFoundation.cursor(disabled)),
        stateValues:{selected,disabled},stateMotions:{selected:{durationMs:140,easing:'out-cubic'},
          hover:{durationMs:110,easing:'out-cubic'}},
        ...(disabled?{}:{onClick:item.onClick}),children:[
          SpectrumFoundation.rect(`${nodeKey}:selection`,1,2,w-indent-2,h-4,6,
            p.selected,undefined,0,{stateSource:'parent',fillReactive:{base:p.selected,
              alphaBase:0,alphaTerms:{selected:1}}}),
          SpectrumFoundation.rect(`${nodeKey}:hover`,1,2,w-indent-2,h-4,6,
            '#00000000',undefined,0,{stateSource:'parent',fillReactive:{base:p.text,
              alphaBase:0,alphaTerms:disabled?{}:{hover:.06,press:.1}}}),
          SpectrumFoundation.text(`${nodeKey}:label`,item.label||'',13,0,w-indent-47,h,
            {size:16,color:disabled?p.disabled:p.text,strong:selected}),
          ...(hasChildren?[ui.svg({key:`${nodeKey}:chevron`,asset:item.expanded?'chevron-down':'chevron-right',
            tint:disabled?p.disabled:p.muted,interactive:false,
            class:ui.abs(w-indent-31,13,16,16)} )]:[])
        ]}));
      yy+=h+4;
      if(hasChildren&&item.expanded)for(const child of item.children)add(child,level+1);
    };
    for(const item of items)add(item,0);
    return SpectrumFoundation.surface(key,w,yy,children);
  }
}
