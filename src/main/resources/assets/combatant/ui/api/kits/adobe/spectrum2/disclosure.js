/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumDisclosure {
  static render(props={}) {
    const key=ui.str(props.key,'s2:disclosure'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(170,ui.num(props.width,450));
    const size=ui.str(props.size,'M').toUpperCase(),rowH=({S:40,M:48,L:56,XL:64})[size]||48;
    const expanded=!!props.expanded,disabled=!!props.disabled,quiet=!!props.quiet;
    const panelH=Math.max(42,ui.num(props.panelHeight,86));
    const children=[];
    if(!quiet)children.push(SpectrumFoundation.rect(`${key}:divider`,0,0,width,1,0,p.border));
    children.push(ui.button({key:`${key}:trigger`,class:ui.abs(0,0,width,rowH,
      SpectrumFoundation.cursor(disabled)),stateValues:{disabled,expanded},
      stateMotions:{hover:{durationMs:125,easing:'out-cubic'},focusVisible:{durationMs:110,easing:'out-cubic'}},
      ...(!disabled?{onClick:props.onClick}:{}),children:[
      SpectrumFoundation.rect(`${key}:hover`,0,0,width,rowH,0,'#00000000',undefined,0,
        {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.05,press:.09}}}),
      ...[[1,1,width-2,2],[1,rowH-3,width-2,2],[1,3,2,rowH-6],
        [width-3,3,2,rowH-6]].map(([a,b,c,d],i)=>SpectrumFoundation.rect(
          `${key}:focus:${i}`,a,b,c,d,0,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:p.focus,alphaBase:0,
              alphaTerms:disabled?{}:{focusVisible:1}}})),
      SpectrumFoundation.text(`${key}:heading`,props.title||'Details',12,0,width-56,rowH,
        {size:({S:15,M:17,L:19,XL:21})[size]||17,strong:true,
          color:disabled?p.disabled:p.text}),
      ui.svg({key:`${key}:chevron`,asset:expanded?'chevron-down':'chevron-right',
        tint:disabled?p.disabled:p.muted,interactive:false,
        class:ui.abs(width-31,(rowH-18)/2,18,18)})
    ]}));
    if(expanded)children.push(ui.stack({key:`${key}:panel`,
      class:ui.abs(0,rowH,width,panelH,'clip overflow-hidden'),children:[
        SpectrumFoundation.text(`${key}:content`,ui.str(props.content,''),14,11,width-28,panelH-14,
          {size:16,color:p.muted,wrap:true,maxLines:4})
      ]}));
    if(!quiet)children.push(SpectrumFoundation.rect(`${key}:end`,0,rowH+(expanded?panelH:0),width,1,0,p.border));
    return SpectrumFoundation.surface(key,width,rowH+(expanded?panelH:0)+(quiet?0:1),children);
  }
}
