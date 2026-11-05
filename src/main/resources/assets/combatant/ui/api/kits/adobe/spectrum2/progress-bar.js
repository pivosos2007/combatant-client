/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumProgressBar {
  static render(props={}) {
    const key=ui.str(props.key,'s2:progress'),palette=SpectrumFoundation.colors(props.colors);
    const width=Math.max(100,ui.num(props.width,420));
    const min=ui.num(props.min,0),max=Math.max(min+0.0001,ui.num(props.max,100));
    const value=Math.min(max,Math.max(min,ui.num(props.value,min))),fraction=(value-min)/(max-min);
    const height=props.size==='S'?4:props.size==='L'?10:6;
    return SpectrumFoundation.surface(key,width,54,[
      SpectrumFoundation.text(`${key}:label`,props.label||'Progress',0,0,width-80,28,{size:16,strong:true}),
      SpectrumFoundation.text(`${key}:value`,ui.str(props.valueLabel,`${Math.round(fraction*100)}%`),width-80,0,80,28,
        {size:16,color:palette.muted,align:'right'}),
      SpectrumFoundation.rect(`${key}:track`,0,39,width,height,height/2,palette.raised),
      SpectrumFoundation.rect(`${key}:fill`,0,39,Math.max(0.01,width*fraction),height,height/2,palette.accent)
    ]);
  }
}
