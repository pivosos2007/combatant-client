/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumTag {
  static render(props={}) {
    const key=ui.str(props.key,'s2:tag'),p=SpectrumFoundation.colors(props.colors),
      w=Math.max(48,ui.num(props.width,96));
    return SpectrumFoundation.surface(key,w,28,[
      SpectrumFoundation.rect(`${key}:background`,0,0,w,28,6,p.raised,p.border,1),
      SpectrumFoundation.text(`${key}:label`,props.label||'Tag',8,0,w-16,28,{size:14,align:'center'})]);
  }
}
