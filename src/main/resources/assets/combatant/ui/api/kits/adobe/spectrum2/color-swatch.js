/* Combatant Client (GPL-3.0). Spectrum 2 ColorSwatch.
 * Size, sample aspect ratio and corner rounding are independent options. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumColorSwatch {
  static dimensions(props={}) {
    const sizes={XS:24,S:32,M:40,L:48};
    const side=sizes[ui.str(props.size,'M').toUpperCase()]||40;
    return {width:props.shape==='rectangle'?side*2:side,height:side};
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:color-swatch'),p=SpectrumFoundation.colors(props.colors);
    const {width,height}=SpectrumColorSwatch.dimensions(props);
    const rounding=['none','default','full'].includes(props.rounding)?props.rounding:'default';
    const radius=rounding==='none'?0:rounding==='full'?height/2:Math.min(6,height*.16);
    const corners=ui.corner.all(radius?ui.corner.rounded(radius):ui.corner.square());
    const color=/^#[a-fA-F0-9]{6}([a-fA-F0-9]{2})?$/.test(ui.str(props.color,''))
      ?props.color:p.accent;
    const selected=!!props.selected,disabled=!!props.disabled;
    const children=[
      // Semantic vector geometry: no scissor, no cut-out or rectangle masking.
      ui.shape({key:`${key}:fill`,shape:'box',style:ui.absolute(1,1,width-2,height-2),
        corners,fill:color,interactive:false}),
      ui.shape({key:`${key}:outline`,shape:'box',style:ui.absolute(.5,.5,width-1,height-1),
        corners,fill:'#00000000',stroke:selected?p.focus:p.border,
        strokeWidth:selected?2:1,interactive:false})
    ];
    if(selected)children.push(ui.svg({key:`${key}:selected`,asset:'check',tint:'#FFFFFFFF',
      class:ui.abs((width-18)/2,(height-18)/2,18,18),interactive:false}));
    if(disabled)children.push(
      ui.shape({key:`${key}:unavailable`,shape:'box',style:ui.absolute(1,1,width-2,height-2),
        corners,fill:'#88000000',interactive:false}),
      ui.svg({key:`${key}:disabled-icon`,asset:'x',tint:'#FFFFFFFF',
        class:ui.abs((width-16)/2,(height-16)/2,16,16),interactive:false})
    );
    const action=ui.str(props.onClick,'');
    return action?ui.button({key,class:ui.abs(0,0,width,height,
      SpectrumFoundation.cursor(disabled)),stateValues:{selected,disabled},
      ...(disabled?{}:{onClick:action}),children})
      :SpectrumFoundation.surface(key,width,height,children);
  }
}
