/* Combatant Client (GPL-3.0). Native Spectrum 2 CardView controlled collection.
 * Stable asset IDs, adaptive grid layout and selection; no per-frame JS work. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumCardView {
  static render(props={}) {
    const key=ui.str(props.key,'s2:card-view'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(168,ui.num(props.width,580));
    const items=Array.isArray(props.items)?props.items:[];
    const mode=ui.str(props.selectionMode,'multiple');
    const chosen=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const size=ui.str(props.size,'M');
    const cardWidth={XS:125,S:155,M:186,L:218,XL:250}[size]||186;
    const gap=ui.str(props.density,'regular')==='compact'?10:16;
    const cols=Math.max(1,Math.min(items.length||1,Math.floor((width+gap)/(cardWidth+gap))));
    const cw=(width-(cols-1)*gap)/cols;
    const visualH=props.layout==='waterfall'?125:104;
    const cardH=visualH+67;
    const rows=Math.ceil(items.length/cols),height=rows*cardH+Math.max(0,rows-1)*gap;
    const children=[];
    items.forEach((item,i)=>{
      const id=ui.str(item.id,String(i)),disabled=!!props.disabled||!!item.disabled;
      const selected=mode!=='none'&&chosen.includes(id);
      const x=(i%cols)*(cw+gap),y=Math.floor(i/cols)*(cardH+gap);
      const fg=disabled?p.disabled:p.text;
      const format=ui.str(item.format,'').replace(/^\./,'').toUpperCase();
      const formatW=Math.min(cw-26,Math.max(46,format.length*7+20));
      const cardChildren=[
        SpectrumFoundation.rect(`${key}:card:${id}:surface`,0,0,cw,cardH,12,p.raised,
          selected?p.accent:p.border,selected?2:1),
        SpectrumFoundation.rect(`${key}:card:${id}:hover`,0,0,cw,cardH,12,'#00000000',undefined,0,
          {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.06,press:.11}}}),
        SpectrumFoundation.rect(`${key}:card:${id}:preview`,7,7,cw-14,visualH-10,7,
          ui.str(item.color,'#FF394A79')),
        ...(item.icon?[ui.svg({key:`${key}:card:${id}:icon`,asset:ui.str(item.icon,'layers'),
          tint:p.muted,interactive:false,class:ui.abs(format?18:cw/2-18,18,36,36)}),
          SpectrumFoundation.text(`${key}:card:${id}:preview:metadata`,ui.str(item.previewText,''),
            10,visualH-38,cw-20,29,{size:13,color:p.muted,align:'center'})]:[
          SpectrumFoundation.rect(`${key}:card:${id}:preview:stripe`,17,visualH-39,
            Math.max(12,cw-82),9,4,SpectrumFoundation.withOpacity('#AAFFFFFF',.8))]),
        ...(format?[
          // Optional file-type token is displayed inside the preview, not
          // over the marquee region containing the name.
          SpectrumFoundation.rect(`${key}:card:${id}:format:bg`,cw-formatW-14,14,
            formatW,24,5,SpectrumFoundation.withOpacity(p.accent,.14),
            SpectrumFoundation.withOpacity(p.accent,.55),1),
          ui.text({key:`${key}:card:${id}:format`,text:format,
            color:disabled?p.disabled:p.text,interactive:false,verticalAlign:'center',
            class:ui.abs(cw-formatW-9,15,formatW-10,22,
              'font-Iosevka-Regular font-size-11 text-align-center')})
        ]:[]),
        ...(props.marqueeOnHover?[ui.text({key:`${key}:card:${id}:name`,
          text:ui.str(item.label,id),color:fg,interactive:false,stateSource:'parent',
          runtimeMarquee:true,marqueeSpeed:38,marqueeHoldMs:650,marqueeFadeWidth:10,
          class:ui.abs(13,visualH,cw-26,28,'font-SourceSans3Semibold font-size-16 clip')})]:[
          SpectrumFoundation.text(`${key}:card:${id}:name`,ui.str(item.label,id),13,visualH,
            cw-26,28,{size:16,strong:true,color:fg})]),
        ui.text({key:`${key}:card:${id}:detail`,text:ui.str(item.description,''),
          color:disabled?p.disabled:p.muted,interactive:false,
          ...(props.marqueeOnHover?{stateSource:'parent',runtimeMarquee:true,
            marqueeSpeed:30,marqueeHoldMs:730,marqueeFadeWidth:9}:
            {textFade:true,fadeRight:12}),
          class:ui.abs(13,visualH+26,cw-26,24,'font-SourceSans3 font-size-13 clip')})
      ];
      if(mode!=='none'&&props.selectionStyle!=='highlight'){
        cardChildren.push(SpectrumFoundation.rect(`${key}:card:${id}:check`,cw-34,18,20,20,5,
          selected?p.accent:p.field,selected?p.accent:p.border,1.5));
        if(selected)cardChildren.push(ui.svg({key:`${key}:card:${id}:check:mark`,asset:'check',
          tint:p.onAccent,interactive:false,class:ui.abs(cw-32,20,16,16)}));
      }
      children.push({...ui.button({key:`${key}:card:${id}`,class:ui.abs(0,0,cw,cardH,
        SpectrumFoundation.cursor(disabled)),stateValues:{selected,disabled},
        stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}},
        ...(disabled||mode==='none'?{}:{onClick:item.onClick}),children:cardChildren}),
        style:ui.absolute(x,y,cw,cardH)});
    });
    return ui.stack({key,class:ui.abs(0,0,width,height),children});
  }
}
