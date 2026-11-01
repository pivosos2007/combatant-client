/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Spectrum 2 ActionButton, supporting connected ActionButtonGroup geometry. */
import { PlayerModelSpectrumFoundation } from './foundation.js';
export class PlayerModelSpectrumActionButton {
  static render(props={}) {
    const key=ui.str(props.key,'s2:action'),p=PlayerModelSpectrumFoundation.colors(props.colors);
    const width=Math.max(32,ui.num(props.width,126));
    const height=({XS:24,S:32,M:36,L:44,XL:52})[ui.str(props.size,'M').toUpperCase()]||36;
    const radius=Math.max(0,ui.num(props.radius,8));
    const selected=!!props.selected,disabled=!!props.disabled;
    const quiet=!!props.quiet,emphasized=!!props.emphasized;
    const connected=!!props.connected;
    const corners=props.corners||ui.corner.all(radius?ui.corner.rounded(radius):ui.corner.square());
    const surface=disabled?(quiet?'#00000000':p.disabledSurface)
      :selected?(emphasized?p.accent:p.selected):quiet?'#00000000':p.surface;
    const icon=ui.str(props.icon,'');
    const label=ui.str(props.label,'');
    const iconOnly=!!icon&&!label;
    const iconSize=Math.min(20,height-10);
    const iconX=iconOnly?(width-iconSize)/2:12;
    const textX=icon?iconSize+19:8;
    const children=[
      ui.shape({key:`${key}:surface`,shape:'box',style:ui.absolute(0,0,width,height),
        corners,fill:surface,stroke:quiet||connected?undefined:
          disabled?p.disabled:p.border,strokeWidth:quiet||connected?0:1,interactive:false}),
      ui.shape({key:`${key}:hover`,shape:'box',style:ui.absolute(0,0,width,height),
        corners,fill:'#00000000',interactive:false,stateSource:'parent',
        fillReactive:{base:p.text,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.08,press:.13}}}),
      ...(icon?[ui.svg({key:`${key}:icon`,asset:icon,
        tint:disabled?p.disabled:selected&&emphasized?p.onAccent:p.text,
        interactive:false,class:ui.abs(iconX,(height-iconSize)/2,iconSize,iconSize)})]:[]),
      ...(label?[PlayerModelSpectrumFoundation.text(`${key}:label`,label,textX,0,
        Math.max(8,width-textX-8),height,{size:15,strong:selected,
          color:disabled?p.disabled:selected&&emphasized?p.onAccent:p.text,
          align:icon?'left':'center'})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,height,PlayerModelSpectrumFoundation.cursor(disabled)),
      stateValues:{selected,disabled},stateMotions:{hover:{durationMs:140,easing:'out-cubic'},
        press:{durationMs:100,easing:'out-cubic'},selected:{durationMs:160,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
}
