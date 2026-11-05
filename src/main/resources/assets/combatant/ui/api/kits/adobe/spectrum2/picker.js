/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumPicker {
  static render(props={}) {
    const key=ui.str(props.key,'s2:picker'),palette=SpectrumFoundation.colors(props.colors);
    const width=Math.max(130,ui.num(props.width,360)),selected=ui.str(props.value,'');
    const items=Array.isArray(props.items)?props.items:[];
    const found=items.find(item=>item.id===selected);
    const disabled=!!props.disabled,opened=!!props.open,inline=!!props.labelHidden,top=inline?0:30,fieldH=inline?40:44;
    return ui.button({key,class:ui.abs(0,0,width,top+fieldH,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled,selected:opened},
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        ...(!inline?[SpectrumFoundation.text(`${key}:label`,props.label||'Choose option',0,0,width,24,{size:16,strong:true})]:[]),
        SpectrumFoundation.rect(`${key}:field`,0,top,width,fieldH,8,disabled?palette.disabledSurface:palette.field,
          disabled?palette.disabled:opened?palette.accent:palette.border,opened&&!disabled?2:1),
        SpectrumFoundation.rect(`${key}:hover`,0,top,width,fieldH,8,'#00000000',undefined,0,{
          stateSource:'parent',fillReactive:{base:palette.text,alphaBase:0,alphaTerms:{hover:.06,press:.10}}}),
        SpectrumFoundation.text(`${key}:selected`,found?found.label:(selected || props.placeholder||'Select…'),14,top,width-54,fieldH,
          {size:17,color:disabled?palette.disabled:found?palette.text:palette.muted}),
        ui.svg({key:`${key}:chevron`,asset:'chevron-down',tint:disabled?palette.disabled:palette.text,interactive:false,
          class:ui.abs(width-35,top+(fieldH-18)/2,18,18)})
      ]});
  }
}
