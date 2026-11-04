/* Combatant Client (GPL-3.0). Controlled hexadecimal color input via native text bridge. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumColorField {
  static render(props={}){
    const key=ui.str(props.key,'s2:color-field'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(152,ui.num(props.width,320)),disabled=!!props.disabled;
    const focused=!!props.focused&&!disabled&&!props.readOnly;
    const value=ui.str(props.value,'#7B61FF');
    const valid=/^#[0-9a-fA-F]{6}$/.test(value),invalid=!!props.invalid||!valid;
    const fill=valid?('#FF'+value.slice(1)):p.disabledSurface;
    const children=[
      SpectrumFoundation.text(`${key}:label`,ui.str(props.label,'Hex'),0,0,width,24,
        {size:15,strong:true,color:disabled?p.disabled:p.text}),
      SpectrumFoundation.rect(`${key}:background`,0,29,width,44,6,
        disabled?p.disabledSurface:p.field,invalid?p.danger:focused?p.focus:p.border,focused?2:1),
      SpectrumFoundation.rect(`${key}:swatch`,10,40,22,22,4,fill,p.border,1),
      SpectrumFoundation.text(`${key}:value`,value||'#',42,29,width-54,44,
        {size:17,color:disabled?p.disabled:invalid?p.danger:p.text}),
      ...(focused?[SpectrumFoundation.rect(`${key}:caret`,
        Math.min(width-18,42+Math.max(0,ui.num(props.caretOffset,0))),40,1.5,22,0,p.text)]:[]),
      ...(props.description||invalid?[SpectrumFoundation.text(`${key}:help`,
        invalid?ui.str(props.errorMessage,'Enter a 6-digit hex color.'):props.description,
        0,79,width,22,{size:14,color:invalid?p.danger:p.muted})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,props.description||invalid?104:76,
      SpectrumFoundation.cursor(disabled,props.readOnly?'cursor-default':'cursor-text')),
      stateValues:{disabled,focusVisual:focused},
      ...(disabled||props.readOnly?{}:{onClick:props.onFocus||'lab:adobe:color:field:focus'}),children});
  }
}
