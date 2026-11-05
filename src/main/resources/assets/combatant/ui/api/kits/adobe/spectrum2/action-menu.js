/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumActionMenu {
  static render(props={}) {
    const key=ui.str(props.key,'s2:action-menu'),p=SpectrumFoundation.colors(props.colors);
    const w=Math.max(96,ui.num(props.width,135)),h=36,disabled=!!props.disabled;
    return ui.button({key,class:ui.abs(0,0,w,h,SpectrumFoundation.cursor(disabled)),
      ...(disabled?{}:{onClick:props.onClick}),
      stateValues:{selected:!!props.open,disabled},stateMotions:{hover:{durationMs:110,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'},selected:{durationMs:140,easing:'out-cubic'}},children:[
        SpectrumFoundation.rect(`${key}:surface`,0,0,w,h,8,disabled?p.disabledSurface:p.surface,disabled?p.disabled:p.border,1,{
          stateSource:'parent',strokeReactive:{base:p.border,mix:p.focus,mixTerms:{selected:1}}}),
        SpectrumFoundation.rect(`${key}:state`,0,0,w,h,8,'#00000000',undefined,0,{
          stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:disabled?{}:{hover:.08,press:.13}}}),
        SpectrumFoundation.text(`${key}:label`,props.label||'Actions',12,0,w-43,h,{size:15,color:disabled?p.disabled:p.text}),
        ui.svg({key:`${key}:icon`,asset:'ellipsis',tint:disabled?p.disabled:p.text,interactive:false,
          class:ui.abs(w-29,9,18,18)})
      ]});
  }
}
