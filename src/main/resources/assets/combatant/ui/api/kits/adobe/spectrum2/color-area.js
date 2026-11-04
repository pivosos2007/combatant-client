/* Combatant Client (GPL-3.0). ColorArea: a GPU quad gradient, not a grid of nodes. */
import { SpectrumFoundation } from './foundation.js';
import { clampColor, hsvHex, colorHandle } from './color-utils.js';
export class SpectrumColorArea {
  static render(props={}) {
    const key=ui.str(props.key,'s2:color-area'),p=SpectrumFoundation.colors(props.colors);
    const size=Math.max(120,ui.num(props.size,248));
    const h=clampColor(ui.num(props.hue,265),0,360),s=clampColor(ui.num(props.saturation,70),0,100);
    const v=clampColor(ui.num(props.brightness,85),0,100),disabled=!!props.disabled;
    const radius=6;
    // Bilinear RGB interpolation between white/hue/black/black is exactly the HSV
    // saturation-value plane for a fixed hue, with only ONE quad draw call.
    const area=ui.shape({key:`${key}:gradient`,shape:'rounded-gradient-quad',
      style:ui.absolute(0,0,size,size),radius,topLeftColor:'#FFFFFFFF',
      topRightColor:hsvHex(h,100,100),bottomLeftColor:'#FF000000',
      bottomRightColor:'#FF000000',interactive:false});
    const children=[area,
      SpectrumFoundation.rect(`${key}:border`,0,0,size,size,radius,'#00000000',p.border,1),
      ...colorHandle(key,size*s/100,size*(100-v)/100,hsvHex(h,s,v),p)];
    if(disabled)children.push(SpectrumFoundation.rect(`${key}:disabled`,0,0,size,size,radius,'#A0282828'));
    return ui.button({key,class:ui.abs(0,0,size,size,SpectrumFoundation.cursor(disabled,'cursor-crosshair')),
      stateValues:{disabled},...(disabled?{}:{onClick:props.onChange||'lab:adobe:color:noop'}),children});
  }
}
