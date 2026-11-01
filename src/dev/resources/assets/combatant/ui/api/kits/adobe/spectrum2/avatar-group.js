/* Combatant Client (GPL-3.0). Native retained Spectrum 2 AvatarGroup adaptation. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumAvatar } from './avatar.js';
export class SpectrumAvatarGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:avatar-group'),p=SpectrumFoundation.colors(props.colors);
    const size=Math.max(16,Math.min(40,ui.num(props.size,32)));
    const items=Array.isArray(props.items)?props.items:[];
    const max=Math.max(1,Math.floor(ui.num(props.maxVisible,5)));
    const displayed=items.slice(0,max);
    const overlap=Math.round(size*.26),step=size-overlap;
    const groupWidth=displayed.length?size+(displayed.length-1)*step:0;
    const showMore=items.length>displayed.length;
    const trailing=showMore?size+5:0;
    const label=ui.str(props.label,'');
    const textWidth=label?Math.max(80,ui.num(props.labelWidth,170)):0;
    const children=[];
    displayed.forEach((item,i)=>{
      const child=SpectrumAvatar.render({key:`${key}:member:${ui.str(item.id,String(i))}`,
        name:item.name,guest:item.guest,tone:item.tone??i,size,disabled:props.disabled,
        showStroke:true,strokeColor:p.background});
      children.push({...child,style:ui.absolute(i*step,0,size,size)});
    });
    if(showMore){
      children.push(ui.circle({key:`${key}:more:disc`,style:ui.absolute(groupWidth+5,0,size,size),
        fill:p.raised,stroke:p.background,strokeWidth:2,interactive:false}));
      children.push(SpectrumFoundation.text(`${key}:more`,
        `+${items.length-displayed.length}`,groupWidth+7,0,size-4,size,
        {size:size<30?11:13,strong:true,color:p.text,align:'center'}));
    }
    if(label)children.push(SpectrumFoundation.text(`${key}:label`,label,
      groupWidth+trailing+12,0,textWidth,size,{size:16,color:p.muted}));
    return SpectrumFoundation.surface(key,groupWidth+trailing+(label?textWidth+12:0),size,children);
  }
}
