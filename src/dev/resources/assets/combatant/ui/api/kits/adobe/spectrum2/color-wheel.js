/* Combatant Client (GPL-3.0). Static pre-rendered wheel + retained native handle. */
import { SpectrumFoundation } from './foundation.js';
import { clampColor,hsvHex,colorHandle } from './color-utils.js';
export class SpectrumColorWheel {
  static render(props={}){
    const key=ui.str(props.key,'s2:color-wheel'),p=SpectrumFoundation.colors(props.colors);
    const size=Math.max(144,ui.num(props.size,224)),disabled=!!props.disabled;
    const hue=((ui.num(props.hue,265)%360)+360)%360;
    const r=size*.416,theta=hue*Math.PI/180-Math.PI/2;
    const x=size/2+r*Math.cos(theta),y=size/2+r*Math.sin(theta);
    const children=[ui.image({key:`${key}:hue-map`,assetType:'texture',
      asset:'combatant:textures/dev/spectrum2_hue_wheel.png',tint:'#FFFFFFFF',
      interactive:false,style:ui.absolute(0,0,size,size)}),
      ...colorHandle(key,x,y,hsvHex(hue,100,100),p)];
    if(disabled)children.push(ui.shape({key:`${key}:disabled`,shape:'circle',
      style:ui.absolute(0,0,size,size),fill:'#88303030',interactive:false}));
    return ui.button({key,class:ui.abs(0,0,size,size,SpectrumFoundation.cursor(disabled)),
      stateValues:{disabled},...(disabled?{}:{onClick:props.onChange||'lab:adobe:color:noop'}),children});
  }
}
