/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 ComboBox: retained, host-controlled text editing and filtered popover. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumComboBox {
  static render(props={}) {
    const key=ui.str(props.key,'s2:combo'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(205,ui.num(props.width,340)),disabled=!!props.disabled;
    const open=!!props.open&&!disabled;
    const value=ui.str(props.value,'');
    const text=open?ui.str(props.query,''):value;
    const message=props.invalid?ui.str(props.errorMessage,'Choose a valid option.'):
      ui.str(props.description,'');
    const label=ui.str(props.label,'Combo box')+(props.required?' *':'');
    const children=[
      SpectrumFoundation.text(`${key}:label`,label,0,0,width,24,{size:16,strong:true,
        color:disabled?p.disabled:p.text}),
      SpectrumFoundation.rect(`${key}:surface`,0,28,width,44,7,
        disabled?p.disabledSurface:p.field,props.invalid?p.danger:open?p.accent:p.border,
        props.invalid||open?2:1),
      SpectrumFoundation.text(`${key}:text`,text||ui.str(props.placeholder,'Select…'),14,28,width-61,44,
        {size:17,color:disabled?p.disabled:text?p.text:p.muted}),
      ...(open?[SpectrumFoundation.rect(`${key}:caret`,Math.min(width-59,
        14+Math.max(0,ui.num(props.caretOffset,0))),39,1.5,22,0,p.text)]:[]),
      ui.svg({key:`${key}:chevron`,asset:'chevron-down',tint:disabled?p.disabled:p.text,
        interactive:false,class:ui.abs(width-35,42,18,18)}),
      ...(message?[SpectrumFoundation.text(`${key}:help`,message,0,76,width,26,
        {size:14,color:props.invalid?p.danger:p.muted})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,message?104:76,
      disabled?SpectrumFoundation.CURSOR_UNSUPPORTED:'cursor-text'),stateValues:{disabled,selected:open},
      ...(disabled?{}:{onClick:props.onOpen}),children});
  }
}
