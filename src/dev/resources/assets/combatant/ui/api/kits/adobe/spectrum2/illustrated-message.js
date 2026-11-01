/* Combatant Client (GPL-3.0). Spectrum 2 Illustrated Message native adaptation.
 * The geometric document illustration is original and uses retained UI primitives,
 * not proprietary Spectrum illustration assets or external image loads. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumButtonGroup } from './button-group.js';

export class SpectrumIllustratedMessage {
  static render(props={}) {
    const key=ui.str(props.key,'s2:illustrated-message');
    const p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(220,ui.num(props.width,500));
    const size=ui.str(props.size,'M').toUpperCase();
    const scale=size==='S'?.8:size==='L'?1.18:1;
    const horizontal=props.orientation==='horizontal'&&width>=470;
    const imageSize=Math.round(118*scale);
    const iconX=horizontal?14:Math.round((width-imageSize)/2);
    const iconY=horizontal?28:12;
    const textX=horizontal?imageSize+38:12;
    const textW=horizontal?width-textX-12:width-24;
    const titleY=horizontal?14:imageSize+27;
    const buttons=Array.isArray(props.actions)?props.actions:[];
    const buttonH=40;
    const buttonY=horizontal?126:titleY+96;
    const maxButtonsWidth=Math.min(textW,290);
    const height=horizontal?Math.max(imageSize+56,buttonY+buttonH+12):buttonY+buttonH+12;
    const nodes=[
      // Background uses Spectrum's semantic fill, with geometric accent details.
      ui.circle({key:`${key}:illustration:halo`,style:ui.absolute(iconX,iconY,imageSize,imageSize),
        fill:'#FF344671',interactive:false}),
      SpectrumFoundation.rect(`${key}:illustration:sheet`,iconX+imageSize*.25,iconY+imageSize*.16,
        imageSize*.52,imageSize*.66,8,p.raised,'#FF7C9BFF',2),
      SpectrumFoundation.rect(`${key}:illustration:line:1`,iconX+imageSize*.34,iconY+imageSize*.37,
        imageSize*.31,4,2,p.muted),
      SpectrumFoundation.rect(`${key}:illustration:line:2`,iconX+imageSize*.34,iconY+imageSize*.48,
        imageSize*.25,4,2,p.muted),
      ui.circle({key:`${key}:illustration:badge`,style:ui.absolute(iconX+imageSize*.6,
        iconY+imageSize*.60,imageSize*.27,imageSize*.27),fill:p.accent,interactive:false}),
      SpectrumFoundation.text(`${key}:illustration:plus`,'+',iconX+imageSize*.6,
        iconY+imageSize*.6,imageSize*.27,imageSize*.27,{size:20*scale,strong:true,align:'center',color:p.onAccent}),
      SpectrumFoundation.text(`${key}:heading`,props.title||'No items yet',textX,titleY,textW,35,
        {size:size==='L'?23:20,strong:true,color:p.text,align:horizontal?'left':'center'}),
      SpectrumFoundation.text(`${key}:description`,props.description||'',textX,titleY+40,textW,52,
        {size:16,color:p.muted,wrap:true,maxLines:2,align:horizontal?'left':'center'}),
    ];
    if(buttons.length){
      const buttonWidth=Math.min(textW,maxButtonsWidth);
      const group=SpectrumButtonGroup.render({key:`${key}:actions`,width:buttonWidth,
        gap:10,items:buttons.map((action,i)=>({id:ui.str(action.id,`button-${i}`),
          label:action.label,variant:action.variant||'secondary',disabled:!!action.disabled,
          onClick:action.onClick,width:Math.max(80,Math.floor((buttonWidth-10)/Math.max(1,buttons.length)))}))});
      const x=textX+(horizontal?0:Math.max(0,(textW-buttonWidth)/2));
      nodes.push({...group,style:ui.absolute(x,buttonY,buttonWidth,buttonH)});
    }
    return SpectrumFoundation.surface(key,width,height,nodes);
  }
}
