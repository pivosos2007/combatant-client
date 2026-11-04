/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 radio component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumRadio {
  static render(props={}) {
    const key=ui.str(props.key,'s2:radio'),p=SpectrumFoundation.colors(props.colors);
    const selected=!!props.selected,disabled=!!props.disabled;
    const sizeKey=ui.str(props.size,'M').toUpperCase();
    const diameter={S:16,M:18,L:20,XL:22}[sizeKey]||18;
    const height=Math.max(38,diameter+16),label=ui.str(props.label,'');
    const width=Math.max(height,ui.num(props.width,label?174:height));
    const left=(height-diameter)/2;
    const ring=disabled?p.disabled:props.emphasized?p.accent:p.control;
    return ui.button({key,class:ui.abs(0,0,width,height,SpectrumFoundation.cursor(disabled)),
      stateValues:{selected,disabled},stateMotions:{
        selected:{durationMs:145,easing:'out-cubic'},focusVisible:{durationMs:120,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        // The ring thickens from 2 to 5 px when selected; there is no dot element.
        SpectrumFoundation.rect(`${key}:ring`,left,left,diameter,diameter,diameter/2,
          '#00000000',ring,2,{stateSource:'parent',
            strokeWidthMorphSignal:'selected',strokeWidthMorphTo:5}),
        SpectrumFoundation.rect(`${key}:focus`,left-5,left-5,diameter+10,diameter+10,
          (diameter+10)/2,'#00000000',p.focus,2,{stateSource:'parent',
          strokeReactive:{base:p.focus,alphaBase:0,alphaTerms:disabled?{}:{focusVisible:1}}}),
        ...(label?[SpectrumFoundation.text(`${key}:label`,label,height+2,0,
          Math.max(1,width-height-2),height,{size:16,color:disabled?p.disabled:p.text})]:[])
      ]});
  }
}
