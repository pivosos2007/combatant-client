/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 Tooltip — contextual, non-interactive, placement-aware. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumTooltip {
  static render(props={}) {
    const key=ui.str(props.key,'s2:tooltip');
    const p=SpectrumFoundation.colors(props.colors);
    const variant=ui.str(props.variant,'neutral');
    const above=props.placement!=='bottom';
    const width=Math.max(120,Math.min(280,ui.num(props.width,220)));
    const text=ui.str(props.label,'Tooltip');
    const height=64,outerH=height+8;
    const fill=variant==='informative'?p.accent:
      variant==='negative'?'#FFC44545':p.raised;
    const icon=variant==='negative'?'spectrum2/alert-error':null;
    const textX=variant==='neutral'?14:39;
    const y=above?0:8;
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,y,width,height,6,fill),
      ui.svg({key:`${key}:tip`,asset:above?'spectrum2/tooltip-tip-down':
        'spectrum2/tooltip-tip-up',tint:fill,interactive:false,
        style:ui.absolute((width-16)/2,above?height:0,16,8)}),
      ...(icon?[ui.svg({key:`${key}:icon`,asset:icon,tint:'#FFFFFFFF',
        interactive:false,style:ui.absolute(12,y+14,22,22)})]:[]),
      ...(variant==='informative'?[ui.shape({key:`${key}:info-circle`,
        shape:'circle',interactive:false,style:ui.absolute(12,y+16,18,18),
        fill:'#00000000',stroke:'#FFFFFFFF',strokeWidth:1.7}),
        SpectrumFoundation.text(`${key}:info-char`,'i',17,y+15,8,20,
          {color:'#FFFFFFFF',size:14,strong:true,align:'center'})]:[]),
      SpectrumFoundation.text(`${key}:label`,text,textX,y+8,width-textX-12,height-16,
        {color:'#FFFFFFFF',size:16,wrap:true,maxLines:2})
    ];
    return ui.stack({key,interactive:false,style:ui.absolute(0,0,width,outerH),children});
  }
}
