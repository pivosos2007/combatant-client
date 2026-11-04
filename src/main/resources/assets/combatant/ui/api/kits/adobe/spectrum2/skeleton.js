/* Combatant Client (GPL-3.0). Spectrum 2 Skeleton, native loading placeholders.
 * Intentionally static: no JS-frame timer or forced re-layout when loading. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumSkeleton {
  static render(props={}) {
    const key=ui.str(props.key,'s2:skeleton'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(20,ui.num(props.width,300));
    const variant=ui.str(props.variant,'text');
    const color=props.quiet?'#FF383838':p.disabledSurface;
    if(variant==='circle'){
      const d=Math.min(width,Math.max(20,ui.num(props.height,width)));
      return SpectrumFoundation.surface(key,d,d,[
        ui.circle({key:`${key}:circle`,style:ui.absolute(0,0,d,d),fill:color,interactive:false})
      ]);
    }
    if(variant==='rectangle'){
      const height=Math.max(8,ui.num(props.height,100));
      const radius=Math.max(0,Math.min(height/2,ui.num(props.radius,8)));
      return SpectrumFoundation.surface(key,width,height,[
        SpectrumFoundation.rect(`${key}:rectangle`,0,0,width,height,radius,color)
      ]);
    }
    const lines=Math.max(1,Math.min(6,Math.round(ui.num(props.lines,3))));
    const lineHeight=Math.max(8,ui.num(props.lineHeight,13));
    const gap=Math.max(4,ui.num(props.gap,10));
    return SpectrumFoundation.surface(key,width,lines*lineHeight+(lines-1)*gap,
      Array.from({length:lines},(_,i)=>SpectrumFoundation.rect(`${key}:line:${i}`,
        0,i*(lineHeight+gap),i===lines-1&&lines>1?width*.64:width,lineHeight,4,color)));
  }
}
