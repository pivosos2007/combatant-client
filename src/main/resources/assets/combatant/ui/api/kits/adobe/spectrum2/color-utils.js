/* Combatant Client (GPL-3.0). Color-space math for the native Spectrum 2 previews.
 * Hue is degrees; saturation, brightness and alpha are percentages. */
export const clampColor=(v,min,max)=>Math.max(min,Math.min(max,Number.isFinite(v)?v:min));
export function hsvToRgb(h,s,v){
  const hue=((h%360)+360)%360, sat=clampColor(s,0,100)/100, val=clampColor(v,0,100)/100;
  const chroma=val*sat, x=chroma*(1-Math.abs((hue/60)%2-1)), m=val-chroma;
  const rgb=hue<60?[chroma,x,0]:hue<120?[x,chroma,0]:hue<180?[0,chroma,x]:
    hue<240?[0,x,chroma]:hue<300?[x,0,chroma]:[chroma,0,x];
  return rgb.map(channel=>Math.round((channel+m)*255));
}
export function rgbHex(r,g,b){
  return '#FF'+[r,g,b].map(v=>Math.round(clampColor(v,0,255)).toString(16).padStart(2,'0')).join('').toUpperCase();
}
export function hsvHex(h,s,v){return rgbHex(...hsvToRgb(h,s,v));}
export function opacityHex(h,s,v,alpha){
  const a=Math.round(clampColor(alpha,0,100)*255/100).toString(16).padStart(2,'0').toUpperCase();
  return '#'+a+hsvHex(h,s,v).substring(3);
}
export function colorHandle(key,x,y,fill,p,large=false){
  const d=large?28:20;
  return [
    ui.shape({key:`${key}:shadow`,shape:'circle',style:ui.absolute(x-d/2-2,y-d/2-2,d+4,d+4),
      fill:'#A0000000',interactive:false}),
    ui.shape({key:`${key}:outline`,shape:'circle',style:ui.absolute(x-d/2,y-d/2,d,d),
      fill,stroke:'#FFFFFFFF',strokeWidth:3,interactive:false}),
    ui.shape({key:`${key}:inner`,shape:'circle',style:ui.absolute(x-d/2+3,y-d/2+3,d-6,d-6),
      fill:'#00000000',stroke:'#AA000000',strokeWidth:1,interactive:false})
  ];
}

/** Convert ARGB/RGB configuration colors to the Spectrum HSV controls. */
export function argbToHsv(value){
  const raw=String(value||'').replace(/^0x/i,'').replace(/^#/,'');
  const hex=/^[a-fA-F0-9]{6}([a-fA-F0-9]{2})?$/.test(raw)?raw:'FFFFFFFF';
  const a=hex.length===8?parseInt(hex.slice(0,2),16):255;
  const rgb=hex.slice(-6),r=parseInt(rgb.slice(0,2),16)/255;
  const g=parseInt(rgb.slice(2,4),16)/255,b=parseInt(rgb.slice(4,6),16)/255;
  const mx=Math.max(r,g,b),mn=Math.min(r,g,b),d=mx-mn;
  let h=0;
  if(d>0){
    if(mx===r)h=((g-b)/d)%6;
    else if(mx===g)h=(b-r)/d+2;
    else h=(r-g)/d+4;
    h=(h*60+360)%360;
  }
  return {hue:h,saturation:mx===0?0:d/mx*100,brightness:mx*100,alpha:a/255*100};
}
