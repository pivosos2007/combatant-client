/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 Toast: nonmodal, dismissible feedback. Controlled by its owner. */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumToast {
  static render(props={}) {
    const key=ui.str(props.key,'s2:toast');
    const p=SpectrumFoundation.colors(props.colors);
    const variant=ui.str(props.variant,'neutral');
    const width=Math.max(240,ui.num(props.width,420));
    const message=ui.str(props.message,'Status updated');
    const actionLabel=ui.str(props.actionLabel,'');
    const hasAction=actionLabel.length>0;
    // Preserve the identical action layout during exit even though its handler
    // is disabled. The action/close are no longer hit targets while fading.
    // The narrow layout moves the action below the message rather than shrinking it.
    const stackAction=hasAction && width<360;
    const height=stackAction?112:64;
    const color={neutral:p.raised,informative:p.accent,positive:'#FF167C55',
      negative:'#FFC44545'}[variant]||p.raised;
    const hasIcon=variant!=='neutral';
    const iconName={positive:'check',negative:'spectrum2/alert-error'}[variant];
    const left=hasIcon?49:19;
    const closeX=width-41;
    const actionW=88;
    const textW=Math.max(70,closeX-left-(hasAction&&!stackAction?actionW+12:8));
    const children=[
      SpectrumFoundation.rect(`${key}:surface`,0,0,width,height,12,color),
      ...(variant==='informative'?[
        ui.shape({key:`${key}:info:ring`,shape:'circle',interactive:false,
          style:ui.absolute(19,20,20,20),fill:'#00000000',
          stroke:'#FFFFFFFF',strokeWidth:2}),
        SpectrumFoundation.text(`${key}:info:mark`,'i',25,19,9,21,
          {size:15,strong:true,color:'#FFFFFFFF',align:'center'})
      ]:hasIcon?[ui.svg({key:`${key}:icon`,asset:iconName,tint:'#FFFFFFFF',
        interactive:false,class:ui.abs(18,19,22,22)})]:[]),
      SpectrumFoundation.text(`${key}:message`,message,left,10,textW,44,
        {size:17,strong:true,color:'#FFFFFFFF',wrap:true,maxLines:2}),
      SpectrumFoundation.rect(`${key}:close:divider`,closeX-8,13,1,height-26,0,
        '#66FFFFFF'),
      ui.button({key:`${key}:close`,class:ui.abs(closeX,13,28,32,
          props.onClose?'cursor-pointer':'cursor-default'),
        interactive:!!props.onClose,
        ...(props.onClose?{onClick:props.onClose}:{}),children:[
          ui.svg({key:`${key}:close-icon`,asset:'x',tint:'#FFFFFFFF',interactive:false,
            class:ui.abs(5,6,18,18)})
        ]})
    ];
    if(hasAction){
      const x=stackAction?left:closeX-actionW-12;
      const y=stackAction?height-48:13;
      children.push(ui.button({key:`${key}:action`,
        class:ui.abs(x,y,actionW,34,props.onAction?'cursor-pointer':'cursor-default'),
        interactive:!!props.onAction,
        ...(props.onAction?{onClick:props.onAction}:{}),
        stateMotions:{hover:{durationMs:130,easing:'out-cubic'}},children:[
          SpectrumFoundation.rect(`${key}:action:outline`,0,0,actionW,34,17,
            '#00000000','#88FFFFFF',1),
          SpectrumFoundation.rect(`${key}:action:hover`,0,0,actionW,34,17,'#00000000',
            undefined,0,{stateSource:'parent',fillReactive:{base:'#FFFFFFFF',alphaBase:0,
              alphaTerms:{hover:.12,press:.18}}}),
          SpectrumFoundation.text(`${key}:action:label`,actionLabel,6,0,actionW-12,34,
            {size:16,strong:true,color:'#FFFFFFFF',align:'center'})
        ]}));
    }
    return ui.stack({key,interactive:false,class:ui.abs(0,0,width,height),children});
  }
}
