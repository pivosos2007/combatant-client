/* Combatant Client (GPL-3.0). A selectable collection, distinct from ColorSwatch. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumColorSwatchGroup } from './color-swatch-group.js';
export class SpectrumColorSwatchPicker {
  static render(props={}){
    const key=ui.str(props.key,'s2:swatch-picker'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(144,ui.num(props.width,344));
    const items=(Array.isArray(props.items)?props.items:[]).map((item,i)=>({
      ...item,selected:String(item.id)===String(props.selectedId),
      onClick:item.disabled?'':ui.str(item.onClick,ui.str(props.actionPrefix,'')+item.id)
    }));
    const content=SpectrumColorSwatchGroup.render({key:`${key}:items`,width,size:props.size||'M',
      density:props.density||'regular',rounding:props.rounding||'default',items});
    const gap=props.density==='compact'?2:props.density==='spacious'?16:8;
    const side=({XS:24,S:32,M:40,L:48})[ui.str(props.size,'M')]||40;
    const cols=Math.max(1,Math.floor((width+gap)/(side+gap)));
    const count=Math.ceil(items.length/cols),height=32+count*side+Math.max(0,count-1)*gap;
    return SpectrumFoundation.surface(key,width,height,[
      SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Preset colors'),0,0,width,25,
        {size:16,strong:true,color:p.text}),
      {...content,style:ui.absolute(0,32,width,height-32)}
    ]);
  }
}
