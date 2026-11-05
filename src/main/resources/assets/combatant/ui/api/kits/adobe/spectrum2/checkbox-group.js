/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumCheckbox } from './checkbox.js';

export class SpectrumCheckboxGroup {
  static height(props={}) {
    const items=Array.isArray(props.items)?props.items:[];
    const gap=Math.max(0,ui.num(props.gap,4));
    const horizontal=props.orientation==='horizontal';
    const width=Math.max(1,ui.num(props.width,360));
    const slots=horizontal?Math.max(1,Math.min(items.length,Math.floor(width/114))):1;
    const rows=Math.ceil(items.length/slots);
    return 51+rows*40+Math.max(0,rows-1)*gap+(props.description||props.errorMessage?35:0);
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:checkbox-group'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(1,ui.num(props.width,360));
    const disabled=!!props.disabled,invalid=!!props.invalid;
    const options=Array.isArray(props.items)?props.items:[];
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const orientation=ui.str(props.orientation,'vertical');
    const horizontal=orientation==='horizontal';
    const gap=Math.max(0,ui.num(props.gap,4));
    // Wrap horizontal items as complete rows. Never place controls beyond the group width.
    const columns=horizontal?Math.max(1,Math.min(options.length,Math.floor(width/114))):1;
    const cellWidth=width/columns;
    const lines=[];
    const title=ui.str(props.label,'Options');
    const necessity=props.required?(props.necessityIndicator==='label'?' (required)':' *'):
      props.optional?' (optional)':'';
    lines.push(SpectrumFoundation.text(`${key}:label`,title+necessity,0,0,width,30,
      {size:17,strong:true,color:disabled?p.disabled:p.text}));
    options.forEach((item,index)=>{
      const id=ui.str(item.id,String(index)),inactive=disabled||!!item.disabled;
      const row=Math.floor(index/columns),col=index%columns;
      const x=col*cellWidth,y=41+row*(40+gap);
      const node=SpectrumCheckbox.render({key:`${key}:item:${id}`,width:cellWidth,
        label:ui.str(item.label,id),selected:selected.includes(id),
        indeterminate:!!item.indeterminate,disabled:inactive,
        emphasized:!!props.emphasized,onClick:item.onClick,colors:props.colors});
      lines.push({...node,style:ui.absolute(x,y,cellWidth,40)});
    });
    const rows=Math.ceil(options.length/columns);
    const helpY=41+rows*40+Math.max(0,rows-1)*gap+8;
    const help=invalid?ui.str(props.errorMessage,'Select at least one option.'):
      ui.str(props.description,'');
    if(help)lines.push(SpectrumFoundation.text(`${key}:help`,help,0,helpY,width,26,
      {size:14,color:disabled?p.disabled:invalid?p.danger:p.muted,wrap:true,maxLines:2}));
    return ui.stack({key,class:ui.abs(0,0,width,SpectrumCheckboxGroup.height(props)),children:lines});
  }
}
