/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumDivider {
  static render(props={}) {
    const key=ui.str(props.key,'s2:divider'),p=SpectrumFoundation.colors(props.colors);
    const vertical=props.orientation==='vertical';
    const size=ui.str(props.size,'S');
    const thickness=({S:1,M:2,L:4})[size]||1;
    const width=vertical?thickness:Math.max(1,ui.num(props.width,220));
    const height=vertical?Math.max(1,ui.num(props.height,90)):thickness;
    const color=props.staticColor==='white'?'#FFFFFFFF':props.staticColor==='black'?'#FF000000':p.border;
    return SpectrumFoundation.surface(key,width,height,[
      ui.shape({key:`${key}:line`,shape:'rect',style:ui.absolute(0,0,width,height),
        fill:color,interactive:false})]);
  }
}
