/* Combatant Client (GPL-3.0). Spectrum 2 RadioGroup, one controlled selection. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumRadio } from './radio.js';
export class SpectrumRadioGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:radio-group'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(145,ui.num(props.width,380)),disabled=!!props.disabled;
    const items=Array.isArray(props.items)?props.items:[];
    const columns=props.orientation==='horizontal'?
      Math.max(1,Math.min(items.length,Math.floor(width/136))):1;
    const cellWidth=width/columns,rows=Math.ceil(items.length/columns);
    const children=[SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Options')+
      (props.required?' *':props.optional?' (optional)':''),0,0,width,29,
      {size:17,strong:true,color:disabled?p.disabled:p.text})];
    items.forEach((item,i)=>{
      const id=ui.str(item.id,String(i)),inactive=disabled||!!item.disabled;
      const node=SpectrumRadio.render({key:`${key}:item:${id}`,width:cellWidth,
        label:ui.str(item.label,id),selected:props.selectedId===id,disabled:inactive,
        emphasized:!!props.emphasized,onClick:item.onClick});
      children.push({...node,style:ui.absolute((i%columns)*cellWidth,
        39+Math.floor(i/columns)*43,cellWidth,38)});
    });
    const h=42+rows*43+(props.description||props.invalid?32:0);
    if(props.description||props.invalid)children.push(SpectrumFoundation.text(`${key}:help`,
      props.invalid?ui.str(props.errorMessage,'Select an option.'):props.description,
      0,43+rows*43,width,25,{size:14,color:props.invalid?p.danger:p.muted}));
    return ui.stack({key,class:ui.abs(0,0,width,h),children});
  }
}
