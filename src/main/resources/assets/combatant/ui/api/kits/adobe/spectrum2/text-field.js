/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 textField component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumTextField {
  static render(props={}) {
    const key=ui.str(props.key,'s2:field'),p=SpectrumFoundation.colors(props.colors),
      width=Math.max(160,ui.num(props.width,350)),disabled=!!props.disabled,
      focused=!!props.focused&&!disabled,
      value=ui.str(props.value,''),isQuiet=!!props.quiet;
    const children=[
      SpectrumFoundation.text(`${key}:label`,props.label||'Field',0,0,width,24,
        {size:15,strong:true,color:disabled?p.disabled:p.text}),
      SpectrumFoundation.rect(`${key}:background`,0,28,width,44,6,
        disabled?p.disabledSurface:isQuiet?'#00000000':p.field,disabled?p.disabled:p.border,1,{
          stateSource:'parent',strokeReactive:{base:disabled?p.disabled:p.border,mix:p.accent,
            mixTerms:{focusVisual:1}},
          strokeWidthMorphSignal:'focusVisual',strokeWidthMorphTo:2}),
      SpectrumFoundation.text(`${key}:value`,value||props.placeholder||'',13,28,width-26,44,
        {size:17,color:disabled?p.disabled:value?p.text:p.muted}),
      ...(focused?[SpectrumFoundation.rect(`${key}:caret`,
        Math.min(width-16,13+Math.max(0,ui.num(props.caretOffset,0))),39,1.5,22,0,p.text)]:[]),
      ...(props.description?[SpectrumFoundation.text(`${key}:description`,props.description,0,77,width,22,
        {size:14,color:disabled?p.disabled:p.muted})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,props.description?100:74,
      disabled?SpectrumFoundation.CURSOR_UNSUPPORTED:(props.readOnly?'': 'cursor-text')),stateValues:{focusVisual:focused,disabled},
      stateMotions:{focusVisual:{durationMs:150,easing:'out-cubic'}},
      ...(props.readOnly||disabled?{}:{onClick:props.onClick}),children});
  }
}
