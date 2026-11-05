/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumTextField {
  static render(props={}) {
    const key=ui.str(props.key,'s2:field'),p=SpectrumFoundation.colors(props.colors),
      width=Math.max(160,ui.num(props.width,350)),disabled=!!props.disabled,
      focused=!!props.focused&&!disabled,
      value=ui.str(props.value,''),isQuiet=!!props.quiet;
    const inline=!!props.labelHidden,top=inline?0:28,fieldH=44;
    const scroll=Math.max(0,ui.num(props.scrollOffset,0)),viewW=Math.max(1,width-26);
    const start=ui.num(props.selectionStart,0)-scroll,end=ui.num(props.selectionEnd,0)-scroll;
    const visible=[
      ...(focused && end>start?[SpectrumFoundation.rect(`${key}:selection`,
        Math.max(0,start),8,Math.max(0,Math.min(viewW,end)-Math.max(0,start)),28,2,
        SpectrumFoundation.withOpacity(p.accent,.33))]:[]),
      SpectrumFoundation.text(`${key}:value`,value||props.placeholder||'',-scroll,0,viewW+scroll,fieldH,
        {size:17,color:disabled?p.disabled:value?p.text:p.muted}),
      ...(focused && end<=start?[SpectrumFoundation.rect(`${key}:caret`,
        Math.max(0,Math.min(viewW-2,ui.num(props.caretOffset,0)-scroll)),10,1.5,24,0,p.text)]:[])
    ];
    const children=[
      ...(!inline?[SpectrumFoundation.text(`${key}:label`,props.label||'Field',0,0,width,24,
        {size:15,strong:true,color:disabled?p.disabled:p.text})]:[]),
      SpectrumFoundation.rect(`${key}:background`,0,top,width,fieldH,6,
        disabled?p.disabledSurface:isQuiet?'#00000000':p.field,disabled?p.disabled:p.border,1,{
          stateSource:'parent',strokeReactive:{base:disabled?p.disabled:p.border,mix:p.accent,
            mixTerms:{focusVisual:1}},
          strokeWidthMorphSignal:'focusVisual',strokeWidthMorphTo:2}),
      ui.stack({key:`${key}:viewport`,style:ui.absolute(13,top,viewW,fieldH,{overflow:'hidden'}),children:visible}),
      ...(props.description?[SpectrumFoundation.text(`${key}:description`,props.description,0,top+fieldH+5,width,22,
        {size:14,color:disabled?p.disabled:p.muted})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,top+fieldH+(props.description?28:0),
      disabled?SpectrumFoundation.CURSOR_UNSUPPORTED:(props.readOnly?'': 'cursor-text')),stateValues:{focusVisual:focused,disabled},
      stateMotions:{focusVisual:{durationMs:150,easing:'out-cubic'}},
      ...(props.readOnly||disabled?{}:{onClick:props.onClick}),children});
  }
}
