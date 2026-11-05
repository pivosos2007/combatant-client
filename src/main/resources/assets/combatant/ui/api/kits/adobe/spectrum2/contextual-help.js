/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumContextualHelp {
  static render(props={}) {
    const key=ui.str(props.key,'s2:contextual-help'),p=SpectrumFoundation.colors(props.colors);
    const size=props.size==='S'?32:24,disabled=!!props.disabled;
    const icon=props.variant==='info'?'i':'?';
    return ui.button({key,class:ui.abs(0,0,size,size,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled,selected:!!props.open},
      ...(disabled?{}:{onClick:props.onClick}),children:[
      SpectrumFoundation.rect(`${key}:hover`,0,0,size,size,size/2,'#00000000',undefined,0,
        {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:{hover:.1,press:.16}}}),
      SpectrumFoundation.rect(`${key}:circle`,(size-18)/2,(size-18)/2,18,18,9,
        '#00000000',disabled?p.disabled:p.muted,1.5),
      SpectrumFoundation.text(`${key}:icon`,icon,0,0,size,size,{size:14,strong:true,
        align:'center',color:disabled?p.disabled:p.muted})
    ]});
  }
  static popover(props={}) {
    const key=ui.str(props.key,'s2:contextual-help:popover'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(200,ui.num(props.width,310));
    const hasFooter=!!props.footer;
    const h=hasFooter?158:128;
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,width,h,12,p.raised,p.border,1),
      SpectrumFoundation.text(`${key}:title`,ui.str(props.title,'Help'),18,13,width-36,28,
        {size:18,strong:true}),
      SpectrumFoundation.text(`${key}:content`,ui.str(props.content,''),18,45,width-36,
        hasFooter?66:70,{size:15,color:p.muted,wrap:true,maxLines:3})
    ];
    if(hasFooter)children.push(SpectrumFoundation.text(`${key}:footer`,props.footer,18,117,
      width-36,26,{size:14,strong:true,color:p.accent}));
    return ui.stack({key,class:ui.abs(0,0,width,h),children});
  }
}
