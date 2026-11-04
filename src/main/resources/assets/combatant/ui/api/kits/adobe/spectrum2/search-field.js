/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Adobe Spectrum 2 searchField component; not Adobe upstream source code. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumSearchField {
  static render(props={}) {
    const key=ui.str(props.key,'s2:search'),palette=SpectrumFoundation.colors(props.colors),
      width=Math.max(220,ui.num(props.width,420)),value=ui.str(props.value,''),
      disabled=!!props.disabled,focused=!!props.focused&&!disabled;
    const labelHidden=!!props.labelHidden,top=labelHidden?0:30;
    const scrollOffset=Math.max(0,ui.num(props.scrollOffset,0));
    const textW=Math.max(1,width-88);
    const caretX=Math.max(0,ui.num(props.caretOffset,0))-scrollOffset;
    const selectionX=Math.max(0,ui.num(props.selectionStart,0))-scrollOffset;
    const selectionEnd=Math.max(0,ui.num(props.selectionEnd,0))-scrollOffset;
    const textNodes=[];
    if(focused && value && selectionEnd>selectionX)textNodes.push(
      SpectrumFoundation.rect(`${key}:selection`,Math.max(0,selectionX),9,
        Math.max(0,Math.min(textW,selectionEnd)-Math.max(0,selectionX)),27,2,
        SpectrumFoundation.withOpacity(palette.accent,.35)));
    textNodes.push(SpectrumFoundation.text(`${key}:text`,value||props.placeholder||'Search…',
      -scrollOffset,0,textW+scrollOffset,44,
      {size:17,color:disabled?palette.disabled:value?palette.text:palette.muted}));
    if(focused && selectionEnd<=selectionX)textNodes.push(
      SpectrumFoundation.rect(`${key}:caret`,Math.max(0,Math.min(textW-2,caretX)),11,1.5,23,0,palette.text));
    const children=[
      ...(!labelHidden?[SpectrumFoundation.text(`${key}:label`,props.label||'Search',0,0,width,24,
        {size:16,strong:true,color:disabled?palette.disabled:palette.text})]:[]),
      SpectrumFoundation.rect(`${key}:field`,0,top,width,44,8,disabled?palette.disabledSurface:palette.field,disabled?palette.disabled:palette.border,1,{
        stateSource:'parent',strokeReactive:{base:disabled?palette.disabled:palette.border,mix:palette.accent,
          mixTerms:{focusVisual:1}},
        strokeWidthMorphSignal:'focusVisual',strokeWidthMorphTo:2}),
      ui.svg({key:`${key}:glass`,asset:'search',tint:disabled?palette.disabled:palette.muted,interactive:false,class:ui.abs(13,top+12,20,20)}),
      ui.stack({key:`${key}:value-viewport`,style:ui.absolute(43,top,textW,44,{overflow:'hidden'}),children:textNodes})
    ];
    if(value&&!disabled)children.push(ui.button({key:`${key}:clear`,onClick:props.onClear,
      class:ui.abs(width-41,top+3,37,38,'cursor-pointer'),children:[
        ui.svg({key:`${key}:clear-icon`,asset:'x',tint:palette.text,interactive:false,
          class:ui.abs(10,9,18,18)})
      ]}));
    return ui.button({key,class:ui.abs(0,0,width,top+44,SpectrumFoundation.cursor(disabled,'cursor-text')),
      stateValues:{focusVisual:focused,disabled},
      stateMotions:{focusVisual:{durationMs:150,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
}
