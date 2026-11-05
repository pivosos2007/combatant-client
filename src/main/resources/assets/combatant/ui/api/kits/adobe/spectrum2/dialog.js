/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumButton } from './button.js';
export class SpectrumDialog {
  static render(props={}) {
    const key=ui.str(props.key,'s2:alert-dialog'),
      full=SpectrumFoundation.colors(props.colors),phase=Math.max(0,Math.min(1,ui.num(props.progress,1)));
    const variant=ui.str(props.variant,'confirmation');
    const compact=!!props.compact;
    const w=Math.max(290,ui.num(props.width,512));
    const title=ui.str(props.title,'Confirm action');
    const description=ui.str(props.description,props.message||'');
    const cancelLabel=props.cancelLabel===null?'':ui.str(props.cancelLabel,'Cancel');
    const secondaryLabel=ui.str(props.secondaryActionLabel,'');
    const primaryLabel=ui.str(props.primaryActionLabel,props.confirmLabel||'Confirm');
    const count=1+(cancelLabel?1:0)+(secondaryLabel?1:0);
    const stackButtons=compact||w<415;
    const h=stackButtons?316+(count===3?42:0):252;
    const fade=c=>SpectrumFoundation.withOpacity(c,phase);
    const p=Object.fromEntries(Object.entries(full).map(([k,v])=>[k,fade(v)]));
    const destructive=variant==='destructive';
    const iconName=variant==='warning'?'spectrum2/alert-warning':
      variant==='error'?'spectrum2/alert-error':null;
    const iconColor=variant==='warning'?'#FFF3B34B':full.danger;
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,w,h,12,fade(full.raised),fade(full.border),1),
      ...(iconName?[ui.svg({key:`${key}:icon`,asset:iconName,tint:fade(iconColor),interactive:false,
        class:ui.abs(32,29,24,24)} )]:[]),
      SpectrumFoundation.text(`${key}:title`,title,iconName?66:32,25,w-(iconName?98:64),42,
        {size:23,strong:true,color:fade(full.text),wrap:true,maxLines:2}),
      SpectrumFoundation.text(`${key}:content`,description,32,86,w-64,stackButtons?98:106,
        {size:17,color:fade(full.muted),wrap:true,maxLines:4})
    ];
    const defs=[];
    if(cancelLabel)defs.push({id:'cancel',label:cancelLabel,variant:'secondary',outline:true,
      onClick:props.onCancel});
    if(secondaryLabel)defs.push({id:'secondary',label:secondaryLabel,variant:'secondary',outline:true,
      disabled:!!props.isSecondaryActionDisabled,onClick:props.onSecondary});
    defs.push({id:'confirm',label:primaryLabel,variant:destructive?'negative':
      variant==='confirmation'?'accent':'primary',outline:variant!=='confirmation',
      disabled:!!props.isPrimaryActionDisabled,onClick:props.onConfirm});
    const actionW=stackButtons?Math.max(130,w-64):count===3?126:146;
    const gap=10;
    const start=stackButtons?32:w-32-count*actionW-(count-1)*gap;
    defs.forEach((d,i)=>{
      const x=stackButtons?32:start+i*(actionW+gap);
      const y=stackButtons?h-32-count*48+i*48:h-72;
      const button=SpectrumButton.render({key:`${key}:${d.id}`,variant:d.variant,
        outline:d.outline,disabled:d.disabled,label:d.label,width:actionW,onClick:d.onClick,colors:p});
      children.push({...button,style:ui.absolute(x,y,actionW,40)});
    });
    return SpectrumFoundation.surface(key,w,h,children);
  }
}
