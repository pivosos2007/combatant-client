/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';

// Semantic colors are intentional sRGB approximations for Combatant's dark palette.
// Never infer semantic severity from the displayed text.
const tones=Object.freeze({
  neutral:'#FFD4D4D4',accent:'#FF7394FF',informative:'#FF76ACFA',
  positive:'#FF67C58A',notice:'#FFF2BF55',negative:'#FFF8796E',
  gray:'#FFC0C0C0',red:'#FFF8796E',orange:'#FFF19B60',yellow:'#FFF2CF69',
  chartreuse:'#FFD0D86A',celery:'#FFB7DC81',green:'#FF67C58A',
  seafoam:'#FF71D2AC',cyan:'#FF71CCD9',blue:'#FF76ACFA',
  indigo:'#FF9699F6',purple:'#FFBBA0F7',fuchsia:'#FFE49DD6',
  magenta:'#FFE69AC7',pink:'#FFEC9CAF',turquoise:'#FF68D3C8',
  brown:'#FFCCAF91',cinnamon:'#FFD4A17D',silver:'#FFC8CFD7'
});
const sizes=Object.freeze({S:[24,13,11],M:[28,14,13],L:[32,16,15],XL:[36,17,17]});
export class SpectrumBadge {
  static render(props={}) {
    const key=ui.str(props.key,'s2:badge'),p=SpectrumFoundation.colors(props.colors);
    const [height,font,padding]=sizes[ui.str(props.size,'S')]||sizes.S;
    const label=ui.str(props.label,'Badge');
    const width=Math.max(height,ui.num(props.width,Math.ceil(label.length*font*.58+padding*2)));
    const tone=tones[ui.str(props.variant,'neutral')]||tones.neutral;
    const fillStyle=ui.str(props.fillStyle,'bold');
    const bold=fillStyle!=='subtle'&&fillStyle!=='outline';
    const textColor=bold?p.background:tone;
    const surface=fillStyle==='outline'?'#00000000':bold?tone:
      SpectrumFoundation.withOpacity(tone,.17);
    const stroke=fillStyle==='outline'?tone:undefined;
    const content=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,width,height,Math.min(9,height*.3),surface,
        stroke,fillStyle==='outline'?1:0),
      ui.clip({key:`${key}:clip`,x:padding,y:0,w:Math.max(1,width-2*padding),h:height,children:[
        SpectrumFoundation.text(`${key}:label`,label,0,0,Math.max(1,width-2*padding),height,
          {size:font,strong:true,color:textColor,align:'center'})
      ]})
    ];
    return SpectrumFoundation.surface(key,width,height,content);
  }
}
