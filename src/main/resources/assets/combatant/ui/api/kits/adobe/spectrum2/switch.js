/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 switch component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSwitch {
  static render(props={}) {
    const key=ui.str(props.key,'s2:switch'),p=SpectrumFoundation.colors(props.colors),
      selected=!!props.selected,disabled=!!props.disabled,emphasized=!!props.emphasized;
    const label=ui.str(props.label,''),width=label?Math.max(96,ui.num(props.width,360)):54;
    const tone=disabled?p.disabled:emphasized?p.accent:p.control;
    const trackOff=disabled?p.disabledSurface:p.surface;
    return ui.button({key,class:ui.abs(0,0,width,label?40:32,SpectrumFoundation.cursor(disabled)),
      stateValues:{selected,disabled},stateMotions:{
        selected:{durationMs:185,easing:'out-cubic'},focusVisible:{durationMs:110,easing:'out-cubic'},hover:{durationMs:115,easing:'out-cubic'},
        press:{durationMs:85,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        SpectrumFoundation.rect(`${key}:track`,0,4,54,24,12,trackOff,disabled?p.disabled:p.controlMuted,2,{
          stateSource:'parent',fillReactive:{base:trackOff,mix:tone,mixTerms:{selected:1}},
          strokeReactive:{base:disabled?p.disabled:p.controlMuted,mix:tone,mixTerms:{selected:1}}}),
        SpectrumFoundation.rect(`${key}:hover`,0,4,54,24,12,'#00000000',undefined,0,{
          stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.075,press:.13}}}),
        SpectrumFoundation.rect(`${key}:thumb`,6,8,16,16,8,disabled?p.disabled:p.control,undefined,0,{
          stateSource:'parent',translateXTo:26,translateXSignal:'selected',
          fillReactive:{base:disabled?p.disabled:p.control,
            mix:p.background,mixTerms:{selected:1}}}),
        ...(label?[SpectrumFoundation.text(`${key}:label`,label,72,-4,width-72,40,
          {size:18,color:disabled?p.disabled:p.text})]:[]),
        SpectrumFoundation.rect(`${key}:focus`,0,2,54,28,14,'#00000000',p.focus,2,{
          stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,
            alphaTerms:disabled?{}:{focusVisible:1}}})
      ]});
  }
}
