/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumLinkButton {
  static render(props={}) {
    const key=ui.str(props.key,'s2:link-button'),p=SpectrumFoundation.colors(props.colors);
    const disabled=!!props.disabled,quiet=!!props.quiet;
    const width=Math.max(90,ui.num(props.width,182)),height=ui.num(props.height,40);
    const color=disabled?p.disabled:props.variant==='secondary'?p.text:p.accent;
    return ui.button({key,class:ui.abs(0,0,width,height,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},...(disabled?{}:{onClick:props.onClick}),children:[
        SpectrumFoundation.rect(`${key}:hover`,0,0,width,height,7,'#00000000',undefined,0,
          {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.07,press:.12}}}),
        SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Learn more'),10,0,width-34,height,
          {size:16,strong:!quiet,color}),
        SpectrumFoundation.text(`${key}:arrow`,'↗',width-27,0,19,height,
          {size:19,strong:true,color})
    ]});
  }
}
