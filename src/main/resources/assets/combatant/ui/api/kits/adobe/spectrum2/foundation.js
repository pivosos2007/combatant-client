/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
export class SpectrumFoundation {
  static CURSOR_UNSUPPORTED = 'cursor-unsupported';
  static cursor(disabled,enabled='cursor-pointer') {
    return disabled ? SpectrumFoundation.CURSOR_UNSUPPORTED : enabled;
  }
  static dark = Object.freeze({
    // sRGB, 8-bit per channel. Accent blue 800/900/1000 is from Spectrum design-data dark palette.
    background:'#FF1D1D1D', surface:'#FF292929', raised:'#FF333333',
    field:'#FF242424', border:'#FF737373', muted:'#FFB9B9B9', text:'#FFF4F4F4',
    accent:'#FF4069FD', accentHover:'#FF5681FF', accentPress:'#FF6995FE',
    onAccent:'#FFFFFFFF', danger:'#FFF8796E', disabled:'#FF818181',
    control:'#FFD4D4D4', controlMuted:'#FFB0B0B0', disabledSurface:'#FF505050',
    selected:'#FF394A79', focus:'#FF8BAEFF',
  });
  static colors(overrides={}) { return {...SpectrumFoundation.dark,...overrides}; }
  static withOpacity(hex,opacity) {
    if(typeof hex!=='string'||!/^#[0-9a-fA-F]{8}$/.test(hex)) return hex;
    const a=Math.max(0,Math.min(255,Math.round(parseInt(hex.slice(1,3),16)*Math.max(0,Math.min(1,opacity)))));
    return '#'+a.toString(16).padStart(2,'0')+hex.slice(3);
  }
  static size = Object.freeze({small:32,medium:40,large:48,extraLarge:56});
  static rect(key,x,y,w,h,radius,fill,stroke,strokeWidth=0,extra={}) {
    return ui.roundedRect({key,x,y,w,h,radius,fill,stroke,strokeWidth,interactive:false,...extra});
  }
  static text(key,value,x,y,w,h,opts={}) {
    const sz=ui.num(opts.size,16);
    return ui.text({key,text:String(value??''),color:opts.color||SpectrumFoundation.dark.text,
      verticalAlign:opts.wrap?'top':'center',interactive:false,
      ...(opts.wrap?{style:ui.style({whiteSpace:'normal',overflowWrap:'break-word',maxLines:ui.num(opts.maxLines,3)})}:{}),
      ...(opts.colorReactive?{colorReactive:opts.colorReactive,stateSource:'parent'}:{}),
      class:ui.abs(x,y,w,h,`font-${opts.strong?'SourceSans3Semibold':'SourceSans3'} font-size-${ui.fmt(sz)} line-height-${ui.fmt(sz+6)} text-align-${opts.align||'left'}`)});
  }
  static surface(key,w,h,children) {
    return ui.stack({key,class:ui.abs(0,0,w,h),children});
  }
}
