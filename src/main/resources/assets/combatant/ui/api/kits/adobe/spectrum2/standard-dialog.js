/* Combatant Client (GPL-3.0). Adobe Spectrum 2 standard dialog. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumButton } from './button.js';
export class SpectrumStandardDialog {
  static render(props={}) {
    const key=ui.str(props.key,'s2:standard-dialog'),p=SpectrumFoundation.colors(props.colors);
    const w=Math.max(286,ui.num(props.width,480)),compact=w<410;
    const h=Math.max(270,ui.num(props.height,296));
    const nodes=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,w,h,12,p.raised,p.border,1),
      SpectrumFoundation.text(`${key}:heading`,ui.str(props.title,'Project settings'),28,23,w-56,40,
        {size:23,strong:true}),
      SpectrumFoundation.text(`${key}:description`,ui.str(props.description,'Configure your project.'),
        28,68,w-56,52,{size:16,color:p.muted,wrap:true,maxLines:2}),
      SpectrumFoundation.rect(`${key}:rule`,28,130,w-56,1,0,p.border),
      SpectrumFoundation.text(`${key}:content-title`,ui.str(props.contentTitle,'Cloud sync'),28,145,w-56,28,
        {size:18,strong:true}),
      SpectrumFoundation.text(`${key}:content`,ui.str(props.content,'Your settings will be saved for this project.'),
        28,175,w-56,48,{size:16,color:p.muted,wrap:true,maxLines:2})
    ];
    const buttonW=compact?Math.min(150,(w-66)/2):126;
    const actions=[
      {id:'cancel',label:'Cancel',variant:'secondary',outline:true,onClick:props.onCancel},
      {id:'confirm',label:ui.str(props.confirmLabel,'Save'),variant:'accent',onClick:props.onConfirm}
    ];
    actions.forEach((a,i)=>{
      const x=w-28-(2-i)*buttonW-(1-i)*10;
      const y=h-62;
      nodes.push({...SpectrumButton.render({key:`${key}:${a.id}`,width:buttonW,
        label:a.label,variant:a.variant,outline:!!a.outline,onClick:a.onClick}),
        style:ui.absolute(x,y,buttonW,40)});
    });
    return SpectrumFoundation.surface(key,w,h,nodes);
  }
}
