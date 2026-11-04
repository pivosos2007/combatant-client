/* Combatant Client (GPL-3.0). Spectrum 2 progress using native Combatant arc strokes. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumProgressCircle {
  static render(props={}) {
    const key=ui.str(props.key,'s2:progress-circle'),p=SpectrumFoundation.colors(props.colors);
    const size=({S:24,M:40,L:64})[ui.str(props.size,'M')]||40;
    const thick=size===24?3:size===40?4:5;
    const min=ui.num(props.minValue,0),max=ui.num(props.maxValue,100);
    const value=ui.num(props.value,min);
    const fraction=max>min?Math.max(0,Math.min(1,(value-min)/(max-min))):0;
    const indeterminate=!!props.indeterminate;
    // The host owns time. No JS timers: the retained tree updates from the render model.
    const phase=((ui.num(props.phase,0)%1)+1)%1;
    const turn=phase*360;
    const sweep=indeterminate?78+42*(.5-.5*Math.cos(phase*4*Math.PI)):fraction*360;
    const begin=indeterminate?turn-90:-90;
    const stroke=props.staticColor==='white'?'#FFFFFFFF':props.staticColor==='black'?'#FF000000':p.accent;
    const track=SpectrumFoundation.withOpacity(stroke,.23);
    const makeArc=(suffix,start,end,color)=>ui.shape({key:`${key}:${suffix}`,
      shape:'arc',style:ui.absolute(0,0,size,size),thickness:thick,
      radius:(size-thick)*.5, startAngle:start,endAngle:end,stroke:color,
      interactive:false});
    const children=[makeArc('track',0,360,track)];
    if(indeterminate||fraction>0)children.push(makeArc('value',begin,begin+sweep,stroke));
    return SpectrumFoundation.surface(key,size,size,children);
  }
}
