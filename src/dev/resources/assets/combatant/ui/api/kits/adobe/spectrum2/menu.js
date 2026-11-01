/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 menu component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumMenu {
  static render(props={}) {
    const key=ui.str(props.key,'s2:menu'),p=SpectrumFoundation.colors(props.colors);
    const items=Array.isArray(props.items)?props.items:[];
    const w=Math.max(190,ui.num(props.width,264)),rowH=42;
    let y=8;
    const children=[SpectrumFoundation.rect(`${key}:surface`,0,0,w,
      16+items.reduce((sum,item)=>sum+(item.type==='separator'?13:item.type==='header'?30:rowH),0),
      12,p.raised,p.border,1)];
    for(const item of items) {
      const id=ui.str(item.id,'item');
      if(item.type==='separator'){
        children.push(SpectrumFoundation.rect(`${key}:${id}:separator`,10,y+6,w-20,1,0,p.border));
        y+=13;continue;
      }
      if(item.type==='header'){
        children.push(SpectrumFoundation.text(`${key}:${id}:heading`,item.label,16,y,w-30,30,
          {size:13,color:p.muted,strong:true}));
        y+=30;continue;
      }
      const disabled=!!item.disabled,focused=ui.num(props.focusIndex,-1)===items.indexOf(item);
      children.push(ui.button({key:`${key}:item:${id}`,
        ...(disabled?{}:{onClick:ui.str(item.onClick,'')}),
        class:ui.abs(8,y,w-16,rowH,SpectrumFoundation.cursor(disabled)),
        stateValues:{disabled,selected:focused},
        stateMotions:{selected:{durationMs:90,easing:'out-cubic'},hover:{durationMs:90,easing:'out-cubic'}},
        children:[
          SpectrumFoundation.rect(`${key}:item:${id}:state`,0,1,w-16,rowH-2,6,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
              alphaTerms:disabled?{}:{hover:.08,selected:.09,press:.12}}}),
          SpectrumFoundation.text(`${key}:item:${id}:label`,item.label,12,0,w-80,rowH,
            {size:16,color:disabled?p.disabled:p.text}),
          ...(item.shortcut?[SpectrumFoundation.text(`${key}:item:${id}:shortcut`,item.shortcut,w-98,0,77,rowH,
            {size:13,color:p.muted,align:'right'})]:[])
        ]}));
      y+=rowH;
    }
    return SpectrumFoundation.surface(key,w,y+8,children);
  }
}
