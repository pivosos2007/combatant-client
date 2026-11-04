/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 picker component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumPicker {
  static render(props={}) {
    const key=ui.str(props.key,'s2:picker'),palette=SpectrumFoundation.colors(props.colors);
    const width=Math.max(220,ui.num(props.width,360)),selected=ui.str(props.value,'');
    const items=Array.isArray(props.items)?props.items:[];
    const found=items.find(item=>item.id===selected);
    const disabled=!!props.disabled,opened=!!props.open;
    return ui.button({key,class:ui.abs(0,0,width,80,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled,selected:opened},
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        SpectrumFoundation.text(`${key}:label`,props.label||'Choose option',0,0,width,24,{size:16,strong:true}),
        SpectrumFoundation.rect(`${key}:field`,0,30,width,44,8,disabled?palette.disabledSurface:palette.field,
          disabled?palette.disabled:opened?palette.accent:palette.border,opened&&!disabled?2:1),
        SpectrumFoundation.rect(`${key}:hover`,0,30,width,44,8,'#00000000',undefined,0,{
          stateSource:'parent',fillReactive:{base:palette.text,alphaBase:0,alphaTerms:{hover:.06,press:.10}}}),
        SpectrumFoundation.text(`${key}:selected`,found?found.label:(props.placeholder||'Select…'),14,30,width-54,44,
          {size:17,color:disabled?palette.disabled:found?palette.text:palette.muted}),
        ui.svg({key:`${key}:chevron`,asset:'chevron-down',tint:disabled?palette.disabled:palette.text,interactive:false,
          class:ui.abs(width-35,43,18,18)})
      ]});
  }
}
