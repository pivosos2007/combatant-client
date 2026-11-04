/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 DatePicker / DateRangePicker fields. The host owns the calendar popover. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumDatePicker {
  static render(props={}) {
    const key=ui.str(props.key,'s2:date-picker'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(190,ui.num(props.width,310)),disabled=!!props.disabled,invalid=!!props.invalid;
    const active=!!props.open&&!disabled;
    const value=ui.str(props.value,''),label=ui.str(props.label,'Date');
    const message=invalid?ui.str(props.errorMessage,'Choose a valid date.'):
      ui.str(props.description,'');
    return ui.button({key,class:ui.abs(0,0,width,message?105:77,
      disabled?SpectrumFoundation.CURSOR_UNSUPPORTED:props.readOnly?'':'cursor-pointer'),stateValues:{disabled,selected:active},
      ...(disabled||props.readOnly?{}:{onClick:props.onOpen}),children:[
      SpectrumFoundation.text(`${key}:label`,label+(props.required?' *':''),0,0,width,26,
        {size:16,strong:true,color:disabled?p.disabled:p.text}),
      SpectrumFoundation.rect(`${key}:surface`,0,30,width,42,7,
        disabled?p.disabledSurface:p.field,invalid?p.danger:active?p.accent:p.border,
        invalid||active?2:1),
      SpectrumFoundation.text(`${key}:value`,value||ui.str(props.placeholder,'Select a date'),
        14,30,width-60,42,{size:17,color:disabled?p.disabled:value?p.text:p.muted}),
      SpectrumFoundation.rect(`${key}:icon:outer`,width-37,41,20,20,3,'#00000000',disabled?p.disabled:p.text,1.5),
      SpectrumFoundation.rect(`${key}:icon:header`,width-37,46,20,1,0,disabled?p.disabled:p.text),
      SpectrumFoundation.rect(`${key}:icon:day`,width-32,51,4,4,1,disabled?p.disabled:p.text),
      ...(message?[SpectrumFoundation.text(`${key}:help`,message,0,77,width,26,
        {size:14,color:invalid?p.danger:p.muted})]:[])
    ]});
  }
}
