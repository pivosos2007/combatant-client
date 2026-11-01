/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 button component; not Adobe upstream source code. */
import { PlayerModelSpectrumFoundation } from './foundation.js';
export class PlayerModelSpectrumButton {
  static render(props={}) {
    const key=ui.str(props.key,'s2:button');
    const p=PlayerModelSpectrumFoundation.colors(props.colors);
    const variant=ui.str(props.variant,'primary');
    const outline=!!props.outline && (variant==='primary'||variant==='secondary'||variant==='negative');
    const requestedSize=ui.str(props.size,'M');
    const sizeMap={XS:32,S:32,M:40,L:48,XL:56,small:32,medium:40,large:48,extraLarge:56};
    const height=sizeMap[requestedSize]||40;
    const width=Math.max(80,ui.num(props.width,132));
    const radius=height/2, disabled=!!props.disabled;
    const bg=outline?'#00000000':variant==='accent'?p.accent:
      variant==='negative'?p.danger:variant==='secondary'?p.raised:p.text;
    const fg=outline?(variant==='negative'?p.danger:p.text):
      (variant==='accent'||variant==='negative')?p.onAccent:p.background;
    const fill=disabled?(outline?'#00000000':p.disabledSurface):bg;
    const textColor=disabled?p.disabled:fg;
    const stateColor=(variant==='accent'||variant==='negative')?'#FFFFFFFF':
      outline?p.text:'#FF000000';
    const children=[
      PlayerModelSpectrumFoundation.rect(`${key}:container`,0,0,width,height,radius,fill,
        outline?(disabled?p.disabled:variant==='negative'?p.danger:variant==='secondary'?p.border:p.text):undefined,outline?1.5:0),
      PlayerModelSpectrumFoundation.rect(`${key}:state`,0,0,width,height,radius,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:stateColor,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.10,press:.17}}}),
      PlayerModelSpectrumFoundation.text(`${key}:label`,props.label||'Button',12,0,width-24,height,{strong:true,
        align:'center',size:16,color:textColor})
    ];
    return ui.button({key,class:ui.abs(0,0,width,height,PlayerModelSpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
}
