/* Combatant Client (GPL-3.0). Independent Spectrum 2 avatar fallback adaptation. */
import { SpectrumFoundation } from './foundation.js';
const palette=['#FF426A98','#FF745797','#FF2F7E7E','#FF915F45','#FF596D35','#FF866582'];
export class SpectrumAvatar {
  static initials(name) {
    const parts=ui.str(name,'').trim().split(/\s+/).filter(Boolean);
    // Adobe's initials style is limited to Latin names; other scripts use a guest fallback.
    if(parts.length===0||!parts.every(s=>/^[A-Za-z]/.test(s)))return '';
    return (parts[0][0]+(parts.length>1?parts[parts.length-1][0]:'')).toUpperCase();
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:avatar'),p=SpectrumFoundation.colors(props.colors);
    const size=Math.max(16,Math.min(112,ui.num(props.size,40))),disabled=!!props.disabled;
    const initials=props.guest?'':SpectrumAvatar.initials(props.name||props.initials||'');
    const raw=ui.num(props.tone,0),index=Math.abs(Math.floor(raw))%palette.length;
    const color=disabled?p.disabledSurface:palette[index];
    const stroke=props.showStroke?(props.strokeColor||p.surface):undefined;
    const children=[
      ui.circle({key:`${key}:disc`,style:ui.absolute(0,0,size,size),
        fill:color,stroke,strokeWidth:props.showStroke?2:0,interactive:false}),
      ...(initials?[SpectrumFoundation.text(`${key}:initials`,initials,2,0,size-4,size,
        {size:Math.min(27,Math.max(9,size*.39)),color:disabled?p.disabled:'#FFFFFFFF',
          strong:true,align:'center'})]:[
        ui.svg({key:`${key}:guest`,asset:'user',tint:disabled?p.disabled:'#FFFFFFFF',
          class:ui.abs(size*.23,size*.23,size*.54,size*.54),interactive:false})
      ])
    ];
    return SpectrumFoundation.surface(key,size,size,children);
  }
}
