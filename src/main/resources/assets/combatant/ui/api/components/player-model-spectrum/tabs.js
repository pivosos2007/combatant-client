/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 tabs component; not Adobe upstream source code. */
import { PlayerModelSpectrumFoundation } from './foundation.js';
export class PlayerModelSpectrumTabs {
  static render(props={}) {
    const key=ui.str(props.key,'s2:tabs'),p=PlayerModelSpectrumFoundation.colors(props.colors),
      items=Array.isArray(props.items)?props.items:[],w=Math.max(1,ui.num(props.width,480));
    const itemW=w/Math.max(1,items.length),selectedIndex=Math.max(0,items.findIndex(i=>i.id===props.selected));
    const children=[PlayerModelSpectrumFoundation.rect(`${key}:divider`,0,46,w,1,0,p.border),
      PlayerModelSpectrumFoundation.rect(`${key}:indicator`,selectedIndex*itemW+8,45,
        Math.max(1,itemW-16),3,1,p.accent)];
    items.forEach((item,i)=>{
      const selected=item.id===props.selected,disabled=!!item.disabled;
      children.push(ui.button({key:`${key}:tab:${item.id}`,
        ...(disabled?{}:{onClick:item.onClick}),
        class:ui.abs(i*itemW,0,itemW,48,PlayerModelSpectrumFoundation.cursor(disabled)),stateValues:{selected,disabled},
        stateMotions:{selected:{durationMs:180,easing:'out-cubic'},hover:{durationMs:140,easing:'out-cubic'}},
        children:[
          PlayerModelSpectrumFoundation.rect(`${key}:tab:${item.id}:hover`,0,0,itemW,45,6,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:disabled?{}:{hover:.06,press:.1}}}),
          ui.text({key:`${key}:tab:${item.id}:label`,text:item.label,
            interactive:false,verticalAlign:'center',stateSource:'parent',color:p.muted,
            colorReactive:{base:disabled?p.disabled:p.muted,
              mix:disabled?p.disabled:p.text,mixTerms:{selected:1}},
            class:ui.abs(5,0,itemW-10,44,'font-OnestMedium font-size-16 text-align-center')}),
        ]}));
    });
    return PlayerModelSpectrumFoundation.surface(key,w,48,children);
  }
}
