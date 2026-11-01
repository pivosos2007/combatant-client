/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 statusLight component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumStatusLight {
  static render(props={}) {
    const key=ui.str(props.key,'s2:status'),p=SpectrumFoundation.colors(props.colors);
    const tone={positive:'#FF4CAF82',notice:'#FFF6B44D',negative:p.danger,
      informative:p.accent,neutral:p.muted}[props.variant]||p.muted;
    const w=Math.max(70,ui.num(props.width,160));
    return SpectrumFoundation.surface(key,w,28,[
      SpectrumFoundation.rect(`${key}:dot`,1,9,10,10,5,tone),
      SpectrumFoundation.text(`${key}:label`,props.label||'Status',19,0,w-19,28,{size:16})
    ]);
  }
}
