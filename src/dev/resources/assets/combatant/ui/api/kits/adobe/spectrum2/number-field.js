/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 NumberField. Editing and numeric validation belong to the Java text bridge. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumNumberField {
  static render(props={}) {
    const key=ui.str(props.key,'s2:number'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(130,ui.num(props.width,300));
    const disabled=!!props.disabled,readOnly=!!props.readOnly,invalid=!!props.invalid;
    const editable=!disabled&&!readOnly,focus=editable&&!!props.focused;
    const sizes={S:32,M:40,L:48,XL:56};
    const fieldH=sizes[ui.str(props.size,'M')]||40;
    const fieldY=30,stepW=props.hideStepper?0:42;
    const message=invalid?ui.str(props.errorMessage,'Enter a valid number.'):
      ui.str(props.description,'');
    const value=props.value===null||props.value===undefined?'–':String(props.value);
    const label=ui.str(props.label,'Number')+(props.required?(props.necessityIndicator==='label'?' (required)':' *'):'');
    const children=[
      SpectrumFoundation.text(`${key}:label`,label,0,0,width,24,{size:16,strong:true,
        color:disabled?p.disabled:p.text}),
      SpectrumFoundation.rect(`${key}:background`,0,fieldY,width,fieldH,7,
        disabled?p.disabledSurface:p.field,invalid?p.danger:focus?p.accent:p.border,focus||invalid?2:1),
      SpectrumFoundation.text(`${key}:value`,value+(focus?'':ui.str(props.suffix,'')),14,fieldY,
        Math.max(1,width-stepW-24),fieldH,{size:17,color:disabled?p.disabled:p.text}),
      ...(focus?[SpectrumFoundation.rect(`${key}:caret`,Math.min(width-stepW-12,
        14+Math.max(0,ui.num(props.caretOffset,0))),fieldY+10,1.5,fieldH-20,0,p.text)]:[]),
      ...(message?[SpectrumFoundation.text(`${key}:help`,message,0,fieldY+fieldH+4,width,30,
        {size:14,color:disabled?p.disabled:invalid?p.danger:p.muted,wrap:true,maxLines:2})]:[])
    ];
    if(stepW){
      children.push(ui.shape({key:`${key}:split`,shape:'rect',interactive:false,
        style:ui.absolute(width-stepW,fieldY,1,fieldH),fill:p.border}));
      for(const [id,up,y] of [['inc',true,fieldY],['dec',false,fieldY+fieldH/2]]){
        const inactive=!editable||(up?!!props.maxReached:!!props.minReached);
        const h=fieldH/2;
        children.push(ui.button({key:`${key}:${id}`,class:ui.abs(width-stepW+1,y,stepW-2,h,
          SpectrumFoundation.cursor(inactive)),stateValues:{disabled:inactive},
          ...(inactive?{}:{onClick:up?props.onIncrement:props.onDecrement}),children:[
          SpectrumFoundation.rect(`${key}:${id}:hover`,0,0,stepW-2,h,3,'#00000000',undefined,0,
            {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
              alphaTerms:inactive?{}:{hover:.07,press:.12}}}),
          ui.svg({key:`${key}:${id}:icon`,asset:up?'chevron-up':'chevron-down',
            tint:inactive?p.disabled:p.text,interactive:false,
            class:ui.abs((stepW-20)/2,(h-16)/2,16,16)})
        ]}));
      }
      children.push(ui.shape({key:`${key}:step:divider`,shape:'rect',interactive:false,
        style:ui.absolute(width-stepW,fieldY+fieldH/2,stepW,1),fill:p.border}));
    }
    // Text surface remains a dedicated interactive region; stepper buttons are siblings,
    // never nested under it (so stepper clicks cannot focus the input).
    children.push(ui.button({key,class:ui.abs(0,0,width-stepW,fieldY+fieldH,
      editable?'cursor-text':SpectrumFoundation.cursor(disabled)),stateValues:{disabled},
      ...(editable?{onClick:props.onFocus}:{}),children:[]}));
    return ui.stack({key:`${key}:root`,class:ui.abs(0,0,width,fieldY+fieldH+(message?34:0)),children});
  }
}
