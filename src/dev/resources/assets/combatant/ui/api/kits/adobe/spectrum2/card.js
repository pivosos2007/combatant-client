/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 card component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumCard {
  static render(props={}) {
    const key=ui.str(props.key,'s2:card'),p=SpectrumFoundation.colors(props.colors),
      w=Math.max(200,ui.num(props.width,320)),h=Math.max(148,ui.num(props.height,182)),
      selected=!!props.selected,disabled=!!props.disabled;
    const variant=ui.str(props.variant,'primary');
    const fill=variant==='quiet'?'#00000000':variant==='secondary'?p.surface:p.raised;
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,w,h,12,fill,p.border,1,{
        stateSource:'parent',strokeReactive:{base:p.border,mix:p.accent,mixTerms:{selected:1}}}),
      SpectrumFoundation.rect(`${key}:state`,0,0,w,h,12,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:disabled?{}:{hover:.045,press:.065}}}),
      SpectrumFoundation.text(`${key}:title`,props.title||'Card title',18,16,w-36,32,{size:19,strong:true}),
      SpectrumFoundation.text(`${key}:description`,props.description||'',18,53,w-36,46,
        {size:15,color:p.muted}),
      SpectrumFoundation.rect(`${key}:divider`,18,h-49,w-36,1,0,p.border),
      SpectrumFoundation.rect(`${key}:status-dot`,18,h-29,9,9,4.5,
        props.status==='warning'?'#FFF6B44D':'#FF4CAF82'),
      SpectrumFoundation.text(`${key}:status`,props.status==='warning'?'Needs review':'Published',34,h-40,w-52,32,
        {size:14,color:p.muted})
    ];
    return ui.button({key,class:ui.abs(0,0,w,h,SpectrumFoundation.cursor(disabled)),
      stateValues:{selected,disabled},stateMotions:{selected:{durationMs:150,easing:'out-cubic'},
        focusVisible:{durationMs:110,easing:'out-cubic'},
        hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
}
