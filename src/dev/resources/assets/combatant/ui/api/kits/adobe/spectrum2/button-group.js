/* Combatant Client (GPL-3.0). Native Spectrum 2 Button Group composition. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumButton } from './button.js';

export class SpectrumButtonGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:button-group');
    const entries=Array.isArray(props.items)?props.items:[];
    const width=Math.max(100,ui.num(props.width,440));
    const size=ui.str(props.size,'M');
    const height=({XS:32,S:32,M:40,L:48,XL:56})[size]||40;
    const gap=ui.num(props.gap,12);
    const vertical=props.orientation==='vertical';
    const children=[];
    let x=0,y=0;
    for(let i=0;i<entries.length;i++){
      const item=entries[i];
      const w=vertical?width:Math.min(width,Math.max(80,ui.num(item.width,132)));
      if(!vertical&&x>0&&x+w>width){x=0;y+=height+gap;}
      const node=SpectrumButton.render({key:`${key}:item:${ui.str(item.id,`item-${i}`)}`,
        label:item.label,size,variant:item.variant,outline:!!item.outline,
        disabled:!!props.disabled||!!item.disabled,onClick:item.onClick,width:w});
      children.push({...node,style:ui.absolute(x,y,w,height)});
      if(vertical)y+=height+gap;
      else x+=w+gap;
    }
    return SpectrumFoundation.surface(key,width,vertical?Math.max(height,y-gap):y+height,children);
  }
}
