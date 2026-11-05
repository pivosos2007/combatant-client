/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
import { clampColor,hsvHex,opacityHex,colorHandle } from './color-utils.js';
export class SpectrumColorSlider {
  static render(props={}){
    const key=ui.str(props.key,'s2:color-slider'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(112,ui.num(props.width,320)),vertical=props.orientation==='vertical';
    const length=Math.max(80,vertical?ui.num(props.length,230):width);
    const alpha=props.channel==='alpha',disabled=!!props.disabled;
    const hue=((ui.num(props.hue,265)%360)+360)%360;
    const saturation=clampColor(ui.num(props.saturation,70),0,100);
    const brightness=clampColor(ui.num(props.brightness,85),0,100);
    const opacity=clampColor(ui.num(props.alpha,100),0,100);
    const track=24,normalized=(alpha?opacity:hue/360*100)/100;
    const children=[];
    const segments=alpha?1:6;
    const x0=vertical?13:0,y0=vertical?0:13;
    const gradientColors=alpha?['#00'+hsvHex(hue,saturation,brightness).slice(3),hsvHex(hue,saturation,brightness)]:
      [0,60,120,180,240,300,360].map(h=>hsvHex(h,100,100));
    if(vertical)gradientColors.reverse();
    if(alpha){
      // Visible transparency checkerboard, bounded to the slider track.
      const cell=12,cells=Math.ceil(length/cell);
      for(let i=0;i<cells;i++){
        for(let j=0;j<2;j++){
          const along=i*cell,extent=Math.min(cell,length-along);
          if(extent<=0)continue;
          children.push(SpectrumFoundation.rect(`${key}:checker:${i}:${j}`,
            vertical?x0+j*cell:x0+along,vertical?y0+along:y0+j*cell,
            vertical?cell:extent,vertical?extent:cell,0,(i+j)%2?'#FFB2B2B2':'#FFFFFFFF'));
        }
      }
    }
    for(let i=0;i<segments;i++){
      const start=i*length/segments,extent=(i+1)*length/segments-start;
      children.push(ui.shape({key:`${key}:gradient:${i}`,shape:'gradient',
        style:ui.absolute(vertical?x0:x0+start,vertical?y0+start:y0,
          vertical?track:extent+.02,vertical?extent+.02:track),
        startColor:gradientColors[i],endColor:gradientColors[i+1],
        angle:vertical?90:0,interactive:false}));
    }
    children.push(SpectrumFoundation.rect(`${key}:track-outline`,x0,y0,vertical?track:length,
      vertical?length:track,4,'#00000000',p.border,1));
    const px=vertical?x0+track/2:x0+normalized*length,py=vertical?y0+(1-normalized)*length:y0+track/2;
    // The vertical channel increases from bottom to top, like the Spectrum slider.
    const fill=alpha?opacityHex(hue,saturation,brightness,opacity):hsvHex(hue,100,100);
    children.push(...colorHandle(key,px,py,fill,p));
    if(disabled)children.push(SpectrumFoundation.rect(`${key}:disabled`,x0,y0,
      vertical?track:length,vertical?length:track,4,'#AA303030'));
    return ui.button({key,class:ui.abs(0,0,vertical?50:length,vertical?length:50,
      SpectrumFoundation.cursor(disabled)),stateValues:{disabled},
      ...(disabled?{}:{onClick:props.onChange||'lab:adobe:color:noop'}),children});
  }
}
