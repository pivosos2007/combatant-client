/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSlider {
  static render(props={}) {
    const key=ui.str(props.key,'s2:slider'),palette=SpectrumFoundation.colors(props.colors);
    const width=Math.max(170,ui.num(props.width,420));
    const min=ui.num(props.min,0),max=Math.max(min+0.0001,ui.num(props.max,100));
    const value=Math.min(max,Math.max(min,ui.num(props.value,min))),fraction=(value-min)/(max-min);
    const left=12,trackWidth=width-24,thumbX=left+trackWidth*fraction;
    const disabled=!!props.disabled;
    const children=[
      SpectrumFoundation.text(`${key}:label`,props.label||'Amount',0,0,width-70,28,{size:17,strong:true,color:disabled?palette.disabled:palette.text}),
      SpectrumFoundation.text(`${key}:value`,ui.str(props.valueLabel,String(Math.round(value))),width-75,0,75,28,
        {size:16,align:'right',color:disabled?palette.disabled:palette.muted}),
      SpectrumFoundation.rect(`${key}:track`,left,47,trackWidth,5,2.5,palette.raised),
      SpectrumFoundation.rect(`${key}:fill`,left,47,Math.max(.01,trackWidth*fraction),5,2.5,
        disabled?palette.disabled:palette.accent),
      SpectrumFoundation.rect(`${key}:thumb`,thumbX-10,39,20,20,10,disabled?palette.disabled:palette.text,
        disabled?undefined:palette.accent,disabled?0:2),
    ];
    return ui.button({key,class:ui.abs(0,0,width,68,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'}},...(disabled?{}:{onClick:props.onClick}),children});
  }
}
