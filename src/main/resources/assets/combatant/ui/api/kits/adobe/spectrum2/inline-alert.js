/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumButton } from './button.js';

export class SpectrumInlineAlert {
  static height(props={}) {
    const width=Math.max(230,ui.num(props.width,440));
    return (width<340?154:126)+(props.actionLabel?49:0);
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:inline-alert'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(230,ui.num(props.width,440));
    const height=SpectrumInlineAlert.height(props);
    const variant=ui.str(props.variant,'neutral');
    const tone=({neutral:p.border,informative:p.accent,positive:'#FF4CAF82',
      notice:'#FFF6B44D',negative:p.danger})[variant]||p.border;
    const style=['border','subtleFill','boldFill'].includes(props.fillStyle)?props.fillStyle:'border';
    const radius=9;
    const bold=style==='boldFill';
    const background=bold?tone:p.surface;
    const foreground=bold&&variant==='notice'?'#FF1D1D1D':p.text;
    const subdued=bold?foreground:p.muted;
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,width,height,radius,
        background,style==='border'?tone:undefined,style==='border'?2:0)
    ];
    if(style==='subtleFill')children.push(
      SpectrumFoundation.rect(`${key}:tint`,0,0,width,height,radius,
        SpectrumFoundation.withOpacity(tone,.13)));
    if(variant!=='neutral'){
      children.push(ui.stack({key:`${key}:accent-clip`,
        class:'clip overflow-hidden',style:ui.absolute(0,0,4,height),interactive:false,
        children:[SpectrumFoundation.rect(`${key}:accent`,0,0,width,height,radius,
          bold?SpectrumFoundation.withOpacity(foreground,.36):SpectrumFoundation.withOpacity(tone,.96))]}));
    }
    const pad=20;
    const icon=({informative:'spectrum2/alert-info',positive:'check',
      notice:'alert-warning',negative:'alert-error'})[variant];
    if(icon)children.push(ui.svg({key:`${key}:icon`,asset:icon,
      tint:bold?foreground:tone,interactive:false,
      class:ui.abs(width-47,20,23,23)}));
    const contentWidth=width-pad*2-(icon?29:0);
    children.push(
      SpectrumFoundation.text(`${key}:heading`,props.title||'Information',pad,15,
        contentWidth,width<340?51:35,{size:18,strong:true,color:foreground,wrap:true,maxLines:2}),
      SpectrumFoundation.text(`${key}:content`,props.description||'',pad,width<340?72:56,
        width-pad*2,height-(width<340?84:67)-(props.actionLabel?43:0),
        {size:15,color:subdued,wrap:true,maxLines:4})
    );
    if(props.actionLabel){
      const bw=Math.min(140,width-40);
      const action=SpectrumButton.render({key:`${key}:action`,label:props.actionLabel,
        variant:bold?'primary':'secondary',width:bw,size:'small',
        onClick:props.onAction,disabled:!!props.disabled});
      children.push({...action,style:ui.absolute(pad,height-43,bw,32)});
    }
    return SpectrumFoundation.surface(key,width,height,children);
  }
}
