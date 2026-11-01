/* Combatant Client (GPL-3.0). Native Spectrum 2 RangeSlider; controlled values. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumRangeSlider {
  static render(props={}) {
    const key=ui.str(props.key,'s2:range-slider'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(170,ui.num(props.width,420));
    const min=ui.num(props.minValue,0),max=Math.max(min+1,ui.num(props.maxValue,100));
    const start=Math.max(min,Math.min(max,ui.num(props.startValue,25)));
    const end=Math.max(start,Math.min(max,ui.num(props.endValue,75)));
    const trackX=12,trackW=width-24;
    const lo=trackX+trackW*(start-min)/(max-min),hi=trackX+trackW*(end-min)/(max-min);
    const disabled=!!props.disabled,thick=props.trackStyle==='thick';
    const trackH=thick?8:4,trackY=49-trackH/2;
    const thumbSize=props.thumbStyle==='precise'?16:20;
    const thumbY=49-thumbSize/2;
    const children=[
      SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Range'),0,0,width-122,28,
        {size:17,strong:true,color:disabled?p.disabled:p.text}),
      SpectrumFoundation.text(`${key}:values`,`${Math.round(start)} – ${Math.round(end)}`,
        width-120,0,120,28,{size:16,align:'right',color:disabled?p.disabled:p.muted}),
      SpectrumFoundation.rect(`${key}:track`,trackX,trackY,trackW,trackH,trackH/2,p.raised),
      SpectrumFoundation.rect(`${key}:fill`,lo,trackY,Math.max(.01,hi-lo),trackH,trackH/2,
        disabled?p.disabled:p.accent),
      ...[['start',lo],['end',hi]].map(([id,x])=>[
        SpectrumFoundation.rect(`${key}:${id}:thumb`,x-thumbSize/2,thumbY,thumbSize,thumbSize,
          thumbSize/2,disabled?p.disabled:p.text,disabled?undefined:p.accent,disabled?0:2)
      ]).flat()
    ];
    return ui.button({key,class:ui.abs(0,0,width,72,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},stateMotions:{hover:{durationMs:120,easing:'out-cubic'},
        focusVisible:{durationMs:110,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
}
