/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumLink {
  static render(props={}) {
    const key=ui.str(props.key,'s2:link'),p=SpectrumFoundation.colors(props.colors);
    const label=ui.str(props.label,'Link'),quiet=!!props.quiet,disabled=!!props.disabled;
    const w=Math.max(48,ui.num(props.width,168)),h=Math.max(28,ui.num(props.height,36));
    const color=disabled?p.disabled:props.staticColor==='white'?'#FFFFFFFF':props.staticColor==='black'?'#FF000000':
      props.variant==='secondary'?p.text:p.accent;
    return ui.button({key,class:ui.abs(0,0,w,h,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},...(disabled?{}:{onClick:props.onClick}),
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'},
        focusVisible:{durationMs:110,easing:'out-cubic'}},children:[
      SpectrumFoundation.text(`${key}:text`,label,1,0,w-2,h,{size:17,color,strong:!quiet}),
      ...(!quiet?[SpectrumFoundation.rect(`${key}:underline`,1,h-6,w-2,1,0,color)]:[]),
      SpectrumFoundation.rect(`${key}:focus`,0,2,w,h-4,4,'#00000000',p.focus,2,{
        stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,alphaTerms:{focusVisible:1}}})
    ]});
  }
}
