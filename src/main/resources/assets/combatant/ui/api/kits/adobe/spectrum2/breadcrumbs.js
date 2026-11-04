/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Independent Spectrum 2 Breadcrumbs, including compact overflow trigger. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumBreadcrumbs {
  static layout(items,width,size=16,forceCollapse=false) {
    const measure=label=>Math.max(42,Math.ceil(String(label||'').length*size*.54)+22);
    const full=items.reduce((sum,item)=>sum+measure(item.label)+20,0);
    const collapsed=items.length>3&&(forceCollapse||full>width);
    let visible=items.map((item,i)=>({item,i}));
    let hidden=[];
    if(collapsed){
      let keep=items.length-1;
      let budget=measure(items[0].label)+measure(items[keep].label)+68;
      while(keep>2&&budget+measure(items[keep-1].label)+20<width){
        keep--;budget+=measure(items[keep].label)+20;
      }
      hidden=items.slice(1,keep);
      visible=[{item:items[0],i:0},{item:{id:'__overflow',label:'…'},i:-1},
        ...items.slice(keep).map((item,i)=>({item,i:keep+i}))];
    }
    return {visible,hidden};
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:breadcrumbs'),p=SpectrumFoundation.colors(props.colors);
    const items=Array.isArray(props.items)?props.items:[];
    const width=Math.max(160,ui.num(props.width,520)),height=ui.str(props.size,'M')==='L'?44:36;
    const size=height===44?19:16;
    // Source Sans 3 advance estimate for layout only; the text renderer draws the glyphs.
    const measure=label=>Math.max(42,Math.ceil(String(label||'').length*size*.54)+22);
    const layout=SpectrumBreadcrumbs.layout(items,width,size,props.collapse===true);
    const visible=layout.visible;
    const children=[];
    let x=0;
    visible.forEach(({item,i},n)=>{
      const last=i===items.length-1;
      const overflow=i===-1;
      const disabled=!!item.disabled;
      const w=overflow?38:Math.min(width-x,measure(item.label));
      const nodeKey=`${key}:${overflow?'overflow':item.id}`;
      if(n>0){
        children.push(ui.svg({key:`${nodeKey}:separator`,asset:'chevron-right',
          tint:p.muted,interactive:false,class:ui.abs(x,10,16,16)}));x+=19;
      }
      const actualW=Math.max(1,Math.min(w,width-x));
      if(!last){
        children.push(ui.button({key:nodeKey,
          class:ui.abs(x,0,actualW,height,SpectrumFoundation.cursor(disabled)),
          stateValues:{disabled},stateMotions:{hover:{durationMs:120,easing:'out-cubic'},
            focusVisible:{durationMs:110,easing:'out-cubic'}},
          ...(!disabled?{onClick:overflow?props.onOverflow:item.onClick}:{}),
          children:[
            SpectrumFoundation.rect(`${nodeKey}:hover`,0,1,actualW,height-2,5,'#00000000',undefined,0,
              {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
                alphaTerms:disabled?{}:{hover:.065,press:.11}}}),
            SpectrumFoundation.rect(`${nodeKey}:focus`,1,1,actualW-2,height-2,5,'#00000000',p.focus,2,
              {stateSource:'parent',strokeReactive:{base:p.focus,alphaBase:0,
                alphaTerms:disabled?{}:{focusVisible:1}}}),
            SpectrumFoundation.text(`${nodeKey}:label`,item.label||'',8,0,actualW-10,height,
              {size,strong:false,color:disabled?p.disabled:p.muted})
          ]}));
      } else {
        children.push(SpectrumFoundation.text(`${nodeKey}:current`,item.label||'',x+6,0,
          Math.max(1,actualW-6),height,{size,strong:true,color:p.text}));
      }
      x+=actualW;
    });
    // Exposed for a caller-owned popup, never owns global overlay state itself.
    return SpectrumFoundation.surface(key,width,height,children);
  }
}
