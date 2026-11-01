/* Combatant Client (GPL-3.0). Spectrum 2 swatch collection layout.
 * Density is a property of the group, not of an individual ColorSwatch. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumColorSwatch } from './color-swatch.js';

export class SpectrumColorSwatchGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:swatch-group');
    const width=Math.max(48,ui.num(props.width,340));
    const gap=({compact:2,regular:8,spacious:16})[props.density]??8;
    const items=Array.isArray(props.items)?props.items:[];
    const {width:sw,height:sh}=SpectrumColorSwatch.dimensions(props);
    const columns=Math.max(1,Math.floor((width+gap)/(sw+gap)));
    const children=items.map((item,i)=>{
      const id=ui.str(item.id,`swatch-${i}`);
      const node=SpectrumColorSwatch.render({key:`${key}:${id}`,size:props.size,
        shape:props.shape,rounding:props.rounding,color:item.color,
        selected:!!item.selected,disabled:!!item.disabled,onClick:item.onClick});
      return {...node,style:ui.absolute((i%columns)*(sw+gap),
        Math.floor(i/columns)*(sh+gap),sw,sh)};
    });
    const rows=Math.ceil(items.length/columns);
    return SpectrumFoundation.surface(key,width,rows?rows*sh+(rows-1)*gap:0,children);
  }
}
