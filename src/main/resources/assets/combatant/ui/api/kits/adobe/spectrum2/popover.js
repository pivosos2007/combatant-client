/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumPopover {
  static render(props={}) {
    const key=ui.str(props.key,'s2:popover');
    const p=SpectrumFoundation.colors(props.colors),w=Math.max(195,ui.num(props.width,320));
    const h=Math.max(112,ui.num(props.height,156));
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,w,h,12,p.raised,p.border,1),
      SpectrumFoundation.text(`${key}:heading`,ui.str(props.title,'Popover'),20,18,w-40,32,
        {size:20,strong:true}),
      SpectrumFoundation.text(`${key}:content`,ui.str(props.description,'Additional details.'),20,55,w-40,h-69,
        {size:16,color:p.muted,wrap:true,maxLines:4})
    ];
    return SpectrumFoundation.surface(key,w,h,children);
  }
}
