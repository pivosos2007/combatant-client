/* Combatant Client (GPL-3.0). Spectrum 2 ToggleButton in the native input system. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumToggleButton {
  static render(props={}) {
    const key=ui.str(props.key,'s2:toggle-button'),p=SpectrumFoundation.colors(props.colors);
    const sizes={XS:24,S:32,M:40,L:48,XL:56},height=sizes[ui.str(props.size,'M')]||40;
    const label=ui.str(props.label,'Toggle');
    const width=Math.max(height,ui.num(props.width,132));
    const disabled=!!props.disabled,selected=!!props.selected,quiet=!!props.quiet;
    const emphasized=!!props.emphasized;
    const radius=Math.min(10,height*.23);
    const corners=props.corners||ui.corner.all(ui.corner.rounded(radius));
    const connected=!!props.connected;
    const baseSurface=disabled?p.disabledSurface:quiet?'#00000000':p.surface;
    const selectedSurface=disabled?p.disabledSurface:emphasized?p.accent:p.selected;
    const baseBorder=disabled?p.disabled:quiet?'#00000000':p.border;
    const selectedBorder=disabled?p.disabled:emphasized?p.accent:p.controlMuted;
    const foreground=disabled?p.disabled:p.text;
    return ui.button({key,class:ui.abs(0,0,width,height,SpectrumFoundation.cursor(disabled)),
      stateValues:{selected,disabled},stateMotions:{hover:{durationMs:135,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'},focusVisible:{durationMs:110,easing:'out-cubic'},
        selected:{durationMs:155,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        ui.shape({key:`${key}:surface`,shape:'box',style:ui.absolute(0,0,width,height),
          corners,fill:baseSurface,stroke:connected?undefined:baseBorder,strokeWidth:connected?0:1,interactive:false,
          stateSource:'parent',
          fillReactive:{base:baseSurface,mix:selectedSurface,
            mixTerms:disabled?{}:{selected:1}},
          strokeReactive:{base:baseBorder,mix:selectedBorder,
            mixTerms:disabled?{}:{selected:1}}}),
        ui.shape({key:`${key}:hover`,shape:'box',style:ui.absolute(0,0,width,height),
          corners,fill:'#00000000',interactive:false,
          stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.065,press:.12}}}),
        SpectrumFoundation.text(`${key}:label`,label,10,0,width-20,height,
          {size:height<33?14:16,color:foreground,strong:selected,align:'center',
            ...(emphasized&&!disabled?{colorReactive:{base:p.text,mix:p.onAccent,
              mixTerms:{selected:1}}}:{})}),
        ui.shape({key:`${key}:focus`,shape:'box',style:ui.absolute(-2,-2,width+4,height+4),
          corners:ui.corner.all(ui.corner.rounded(radius+2)),fill:'#00000000',stroke:p.focus,strokeWidth:2,interactive:false,
          stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,
            alphaTerms:disabled?{}:{focusVisible:1}}})
      ]});
  }
}
