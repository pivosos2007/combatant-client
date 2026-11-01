/* Combatant Client (GPL-3.0). Spectrum 2 LabeledValue, native read-only display. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumLabeledValue {
  static render(props={}) {
    const key=ui.str(props.key,'s2:labeled-value'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(110,ui.num(props.width,290));
    const orientation=ui.str(props.orientation,'vertical');
    const horizontal=orientation==='horizontal'&&width>=220;
    const label=ui.str(props.label,'Label'),value=ui.str(props.value,'—');
    const quiet=!!props.quiet;
    const labelW=horizontal?Math.min(170,width*.42):width;
    const labelH=horizontal?40:24;
    const valueX=horizontal?labelW+12:0;
    const height=horizontal?40:70;
    return SpectrumFoundation.surface(key,width,height,[
      SpectrumFoundation.text(`${key}:label`,label,0,0,labelW,labelH,
        {size:15,color:p.muted,strong:!quiet}),
      SpectrumFoundation.text(`${key}:value`,value,valueX,horizontal?0:28,width-valueX,
        horizontal?40:34,{size:quiet?16:18,color:p.text,strong:!quiet})
    ]);
  }
}
