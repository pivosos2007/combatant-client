/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumCheckbox {
  static render(props={}) {
    const key=ui.str(props.key,'s2:checkbox'),p=SpectrumFoundation.colors(props.colors);
    const active=!!props.selected||!!props.indeterminate,disabled=!!props.disabled;
    const label=ui.str(props.label,''),width=label?Math.max(90,ui.num(props.width,280)):40;
    const emphasized=!!props.emphasized, tone=emphasized?p.accent:p.control;
    const stroke=disabled?p.disabled:p.controlMuted;
    const activeFill=disabled?p.disabledSurface:tone;
    const markTone=disabled?p.disabled:p.background;
    return ui.button({key,class:ui.abs(0,0,width,40,SpectrumFoundation.cursor(disabled)),
      stateValues:{selected:active,disabled},stateMotions:{
        selected:{durationMs:150,easing:'out-cubic'},
        focusVisible:{durationMs:110,easing:'out-cubic'},
        hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        SpectrumFoundation.rect(`${key}:box`,10,10,20,20,4,'#00000000',stroke,2,{
          stateSource:'parent',fillReactive:{base:activeFill,alphaBase:0,alphaTerms:{selected:1}},
          strokeReactive:{base:stroke,mix:activeFill,mixTerms:{selected:1}}}),
        ...(props.indeterminate?[SpectrumFoundation.rect(`${key}:mixed`,14,19,12,2,1,markTone,undefined,0,{
          stateSource:'parent',fillReactive:{base:markTone,alphaBase:0,alphaTerms:{selected:1}}})]:[
          ui.svg({key:`${key}:mark`,asset:'check',tint:markTone,interactive:false,
            class:ui.abs(12,12,16,16),stateSource:'parent',
            tintReactive:{base:markTone,alphaBase:0,alphaTerms:{selected:1}}})]),
        ...(label?[SpectrumFoundation.text(`${key}:label`,label,49,0,width-49,40,
          {size:18,color:disabled?p.disabled:p.text})]:[]),
        SpectrumFoundation.rect(`${key}:focus`,6,6,28,28,7,'#00000000',p.focus,2,{
          stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,
            alphaTerms:disabled?{}:{focusVisible:1}}})
      ]});
  }
}
